/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation.tcp;

import java.util.ArrayList;
import java.util.Iterator;

import simulation.network.Endpoint;
import simulation.Simulator;
import simulation.TimedComponent;
import simulation.TimerSimulated;

/**
 * This class implements a simple TCP receiver protocol module.<BR>
 * I tried to follow the specification in
 * <a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>
 * and <a href="http://www.apps.ietf.org/rfc/rfc2581.html" target="page">RFC 2581</a>
 * as closely as I could. However, some intricate details are left out.
 * In particular, because this is a simple discrete-time simulator
 * and the clock tick granularity is very coarse (one tick equals
 * one round-trip time (RTT)), the delayed-ACKs timer is not
 * implemented as recommended. See more discussion related to
 * {@link #delayedACKtimer} and {@link #cumulativeACK}.
 * 
 * @see Simulator
 * @author Ivan Marsic
 */
public class Receiver implements TimedComponent {
	/** Local endpoint that contains this receiver object. */
	Endpoint localEndpoint = null;

	/** The timer for delayed (cumulative) acknowledgments.<BR>
	 * Because of the way the simulator is implemented, it calls
	 * the receiver to process the received packets one by one.
	 * An out-of-order packet must be acknowledged immediately
	 * by a duplicate ACK. However, for in-order packets a
	 * cumulative ACK will be maintained that will be sent
	 * only when this timer expires.</p>
	 * <a href="http://tools.ietf.org/html/rfc2581" target="page">RFC 2581</a>
	 * says that an ACK should be generated for at least every
 	 * second full-sized segment, and must be generated within
	 * 500 ms of the arrival of the first unacknowledged packet.
	 * Therefore, the receiver can send an ACK for no more than
	 * two data packets arriving in-order. See more information
	 * related to {@link #cumulativeACK}. */
	protected TimerSimulated delayedACKtimer = null;

	/**
	 * Handle returned by the simulator, in case {@link #delayedACKtimer}
	 * needs to be canceled. Recall that, if the receiver receives
	 * an out-of-order segment, it is obliged to send a (duplicate)
	 * ACK immediately. However, if {@link #delayedACKtimer} has
	 * not yet expired, it must be cancelled first.
	 */
	protected TimerSimulated delayedACKtimerHandle = null;

	/** Maximum receive window size, in bytes. This is how
	 * much memory this receiver allocated for a temporary
	 * storage ("buffer") for holding out-of-order segments. */
	protected int maxRcvWindowSize = 65536;	// default 64KBytes

	/** Current receive window size, in bytes. Varies depending
	 * on whether any out-of-order segments are currently buffered. */
	protected int currentRcvWindow = 0;

	/** The receiver buffer to buffer the segments that arrive
	 * out-of-sequence. Note that, ideally, this list should always be
	 * sorted in the ascending order of sequence numbers of the
	 * currently buffered segments. Having it sorted makes for
	 * easier processing of gap-filling segments in
	 * {@link #checkBufferedSegments()}. */
	protected ArrayList<Segment> rcvBuffer = new ArrayList<Segment>();

	/** The receiver may hold a cumulative acknowledgment
	 * for in-order segments, to acknowledge several consecutive
	 * segments at once.<BR>
	 * There are two standard methods that can be used by TCP receivers to
	 * generate acknowledgments. The method outlined in
	 * <a href="http://tools.ietf.org/html/rfc793" target="page">RFC 793</a> generates
	 * an ACK for each incoming data segment (including in-order segments).
	 * <a href="http://tools.ietf.org/html/rfc1122" target="page">RFC 1122</a> states
	 * that hosts should use "delayed acknowledgments" for in-order segments.
	 * Using this approach, an ACK is generated for at least every second in-order,
	 * full-sized segment, or if a second full-sized segment does not arrive
	 * within a given timeout (which must not exceed 500 ms [RFC 1122],  and
	 * is typically less than 200 ms).
	 * Such approach is also adopted in
	 * <a href="http://tools.ietf.org/html/rfc2581" target="page">RFC 2581</a>.
	 * <BR><a href="http://tools.ietf.org/html/rfc2760" target="page">RFC 2760</a>
	 * also allows to generate <em>Stretch ACKs</em> that acknowledge more
	 * than two in-order full-sized segments. This approach
	 * provides a possible mitigation, which reduces the rate at which ACKs
	 * are returned by the receiver. The interested reader should check for
	 * discussion of modified delayed ACKs in
	 * <a href="http://tools.ietf.org/html/rfc3449" target="page">RFC 3449</a>
	 * in Section 4.1. */
	protected Segment cumulativeACK = null;

	/** The field records the last byte received in-sequence.
	 * Recall that the bytes are numbered from zero, so the sequence
	 * number of the first byte is zero, etc. */
	protected int lastByteRecvd = -1;

	/** The next byte currently expected from the sender.
	 * Recall that the bytes are numbered from zero, so the sequence
	 * number of the first byte is zero, etc. */
	protected int nextByteExpected = 0;

	/**
	 * Constructor.
	 * @param localTCPendpoint_ The local TCP endpoint object that contains
	 * this receiver.
	 * @param rcvWindowSize_ The maximum receive window size, in bytes
	 * &mdash; how much memory this receiver should allocate for buffering
	 * out-of-order segments.
	 */
	public Receiver(Endpoint localTCPendpoint_, int rcvWindowSize_) {
		this.localEndpoint = localTCPendpoint_;
		this.maxRcvWindowSize = rcvWindowSize_;
		this.currentRcvWindow = this.maxRcvWindowSize;

		// Delayed ACK timer for cumulative ACKs, created but not activated
		delayedACKtimer = new TimerSimulated(
			this, 2 /* type equals "2" */, 0.0
		);
	}

	/** Returns the receive window size for this receiver, in bytes. */
	public int getRcvWindow() {
		return currentRcvWindow;
	}

	/**
	 * Callback method to call when a simulated timer expires. </p>
	 * 
	 * <p>Currently, the receiver sets a timer
	 * for delayed (cumulative) acknowledgments.
	 * Any cumulative ACK that it may be holding
	 * will be transmitted now.
	 * 
	 * @see TimedComponent
	 */
	@Override
	public void timerExpired(int timerType_) {
		delayedACKtimerHandle = null;	// clear the timer handle
		sendCumulativeAcknowledgement();
	}

	/**
	 * Helper method to transmit a cumulative acknowledgment.<BR>
	 * The TCP specification suggests that at <em>least every other</em>
	 * acknowledgment should be sent. However, for the lack of time,
	 * this implementation sends whatever accumulates within
	 * the delayed-ACK timer {@link #delayedACKtimer} time.
	 */
	protected void sendCumulativeAcknowledgement() {
		// first cancel the delayed-ACK timer if it's still running
		if (delayedACKtimerHandle != null) {
			try {
				localEndpoint.getSimulator().cancelTimeout(delayedACKtimerHandle);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			delayedACKtimerHandle = null;	// clear the timer handle
		}
		if (cumulativeACK != null) {
			// Hand the cumulative ACK down to the network layer for transmission
			localEndpoint.getNetworkLayerProtocol().send(localEndpoint, cumulativeACK);
			cumulativeACK = null;
		}
	}

	/**
	 * Receives the segments from the sender, passes the
	 * ones that arrived error-free and in-order to the application.
	 * Buffers the ones that arrived out-of-sequence.<BR>
	 * The receiver quietly discards a packet that arrived with
	 * a checksum error.</p>
	 * 
	 * <p>The receiver returns <i>cumulative</i> acknowledgments,
	 * which means that if the newly received segment fills the
	 * gap created by out-of-sequence segments that were received
	 * earlier, the cumulative ACK will acknowledge those earlier
	 * segments, as well.
	 * <P>
	 * The value <code>null</code> of the <code>segments_</code> input
	 * array element means that the corresponding segment was
	 * <i>lost</i> in transport (i.e., at the Router).
	 * 
	 * @param segment_ The received segments (with non-zero data payload).
	 */
	public void handle(Segment segment_) {
		// Silently discard a packet that arrives with a checksum error
		// because the receiver doesn't know what to do with it:
		if (segment_.inError) {
			return;
		}

		// Check if the segment arrived in-sequence.
		// Recall that we're expecting the segment with
		// sequence number equal "nextByteExpected"
		if (segment_.dataSequenceNumber == nextByteExpected) {

			// Set the expected seq. num. to the next segment.
			nextByteExpected =
				segment_.dataSequenceNumber + segment_.length;

			// Check is there were any out-of-sequence segments
			// previously buffered:
			if (rcvBuffer.isEmpty()) {
				// No previously buffered segments.
				// Make record of the last byte received in-sequence.
				lastByteRecvd =
					segment_.dataSequenceNumber + segment_.length - 1;

			} else {
				// Some segments were previously buffered.
				// Checked whether this segment filled any gaps for
				// the possible buffered segments.  If yes,
				// this will update "lastByteRecvd"
				checkBufferedSegments();
			}

			// Acknowledge the received segment.
			// NOTE: This is a _cumulative_ acknowledgment,
			// in that it possibly acknowledges some segments which
			// were earlier received and buffered, but now the gap
			// was filled.
			if (cumulativeACK == null) {
				cumulativeACK =	new Segment(
					localEndpoint.getRemoteTCPendpoint(),
					currentRcvWindow, nextByteExpected
				);	// ACK segment with zero-length data
				// Bounce back the timestamp of the received data segment
				cumulativeACK.timestamp = segment_.timestamp;

				// Re-start the delayed-ACKs timer for the cumulative ACK
				// using the current time tick, because we know how the
				// simulator works and when it fires the expired timers.
				// That is, the Simulator clock tick equals one RTT and
				// it checks for expired timers at the end of an RTT period.
				delayedACKtimer.setTime(localEndpoint.getSimulator().getCurrentTime());
				try {
					delayedACKtimerHandle =
						localEndpoint.getSimulator().setTimeoutAt(delayedACKtimer);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				// There is already a cumulative ACK waiting
				// just update its parameters.
				cumulativeACK.rcvWindow = currentRcvWindow;
				cumulativeACK.setAckSequenceNumber(nextByteExpected);
				// Bounce back the timestamp of the received data segment
				cumulativeACK.timestamp = segment_.timestamp;
			}
		} //ends IF condition: segments_[i_].seqNum == nextByteExpected

		else {	
			// Out-of-sequence segment, buffer the segment...
			// ...but because the delayed-ACK timer may not
			// have expired, first send a lingering cumulative ACK, if any...
			sendCumulativeAcknowledgement();
			// ...and send a duplicate ACK immediately.
			localEndpoint.getNetworkLayerProtocol().send(
				localEndpoint, handleOutOfSequenceSegment(segment_)
			);
			// This must be a duplicate ACK !!!
		}
		// Display the relevant receiver's parameters.
		if (		// Debugging reporting:
			(Simulator.currentReportingLevel  & Simulator.REPORTING_RECEIVERS) != 0
		) {
			System.out.println(
				"RECEIVER:\tlastByteRecvd="+lastByteRecvd + "\t" + "nextByteExpected="+nextByteExpected +
				"\t" + "currentRcvWindow="+currentRcvWindow
			);
		}
	}

	/**
	 * Helper method to handle out-of-sequence segments.
	 * Such segments are buffered in the {@link #rcvBuffer}.
	 * The returned value will be a <i>duplicate acknowledgment</i>.
	 * 
	 * @param segment_ The segment that is currently being processed
	 * (i.e., the seq. num. of the segment's last byte).
	 * @return Returns the acknowledgment segment for the input data segment.
	 */
	protected Segment handleOutOfSequenceSegment(Segment segment_) {
		// Buffer an out-of-sequence segment.
		// Note that we do NOT assume that currently buffered segments
		// are ordered in the ascending order of their sequence number.
		Segment outOfOrderSeg_ = (Segment) segment_.clone();
		rcvBuffer.add(outOfOrderSeg_);

		// Also, we CANNOT assume that all currently buffered segments
		// have sequence number lower than the one that just arrived.
		lastByteRecvd = Math.max(
			lastByteRecvd,
			outOfOrderSeg_.dataSequenceNumber + outOfOrderSeg_.length - 1
		);

		// Because we just buffered one segment, we may need to reduce
		// the size of the receive window. We cannot simply subtract this
		// segment's length, because this segment may be out-of-order
		// but logically preceding another already buffered segment,
		// so the memory for it may already be held.
		currentRcvWindow = maxRcvWindowSize - (lastByteRecvd - nextByteExpected);

		// Generate a duplicate ACK, to be transmitted immediately !!!
		// Note that by default, the timestamp of this segment will be "-1"
		return new Segment(
			localEndpoint.getRemoteTCPendpoint(),
			currentRcvWindow, nextByteExpected
		);
	}

	/**
	 * Helper method, checks if the newly received segment(s)
	 * fill a gap for the segments that were previously
	 * received out-of-sequence and are stored in a temporary
	 * storage ("buffered").
	 * These segments are waiting for the gap to be filled.
	 * Once an arriving segment fills the gap, the first buffered
	 * segment will become "next expected segment".
	 * That is the condition for which this method checks.
	 * If what is currently the "next expected segment" is one of
	 * already buffered segments (received earlier), this method
	 * removes that segment from the receive buffer and delivers
	 * it to the receiving application.
	 */
	protected void checkBufferedSegments() {

		// Check all the buffered segments, if any,
		// to see if the just-arrived segment filled a gap.
		// Recall that "lastBuffered" is a list that is NOT
		// sorted in a ascending order of segments' sequence
		// numbers.
		// Sort the list first to avoid having inspect all
		// list elements several times to determine if all
		// gaps are filled.
		// Check the method TCPSegment.compareTo() to see
		// how the sorting is performed.
		java.util.Collections.sort(rcvBuffer);

		Iterator<Segment> bufferedItems = rcvBuffer.iterator();
		while (bufferedItems.hasNext()) {

			// Check if the previously buffered out-of-sequence segment
			// is presently in-sequence, so can be removed from the
			// buffer:
			Segment seg = bufferedItems.next();
			if (seg.dataSequenceNumber == nextByteExpected) {

				// Remove the segment from the buffer:
				nextByteExpected = seg.dataSequenceNumber + seg.length;

				// Because we removed one segment from the buffer, we need
				// to _reclaim_ the freed buffer space, and increase the
				// receive window size by the removed segment's length.
				currentRcvWindow =
					maxRcvWindowSize - (lastByteRecvd - nextByteExpected);

				// Perform the segment's removal.
				bufferedItems.remove();
			} else {
				// Quit because the remaining buffered segments
				// are all out-of-order.
				break;
			}
		}
	}
}