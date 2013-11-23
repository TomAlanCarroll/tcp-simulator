/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */

package simulation.tcp;

import java.nio.ByteBuffer;

import simulation.network.Endpoint;
import simulation.Simulator;
import simulation.TimedComponent;
import simulation.TimerSimulated;

/**
 * The base class for a TCP sender protocol module.<BR>
 * Because this is a simple simulator, some intricate details
 * of TCP senders are left out. For example, this code does
 * not implement a <a href="http://www.pcvr.nl/tcpip/tcp_pers.htm" target="page">persist timer</a>
 * as described in <a href="http://tools.ietf.org/html/rfc1122" target="page">RFC 1122</a>
 * Section 4.2.2.17 (related to "zero-window probes", when the receiver
 * reports a zero-size receive window).</p>
 * 
 * <p> <b>Note</b>: If you are in doubt or some of this code is conflicting
 * your textbook, please check the ultimate sources:
 * <a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>
 * and <a href="http://www.apps.ietf.org/rfc/rfc2581.html" target="page">RFC 2581</a>.
 * An older document, although outdated, may also be of interest:
 * <a href="http://www.apps.ietf.org/rfc/rfc2001.html" target="page">RFC 2001</a>.
 * <BR><i>Do not rely on any textbooks for precise details!</i>
 * <BR> Read the textbook(s) for high-level understanding of
 * the material; read the RFCs for precise details.</p>
 * 
 * @see SenderTahoe
 * @see SenderReno
 * @see SenderNewReno
 * @author Ivan Marsic
 */
public abstract class Sender implements TimedComponent {
	/** Maximum segment size, in bytes. Same for both sending/receiving endpoints. */
	public static final int MSS = 1024;

	/** Local endpoint that contains this sender object. */
	Endpoint localEndpoint = null;

	/**
	 * TCP bytestream to send to the receiver endpoint.
	 */
	protected java.nio.ByteBuffer bytestream = null;

 	/** Pointer to the last byte sent so far.
	 * Recall that the bytes are numbered from zero, so the sequence
	 * number of the first byte is zero, etc. */
 	protected int lastByteSent = -1;

 	/** Pointer to the last byte ACKed so far.
 	 * Recall that the bytes are numbered from zero, so the sequence
	 * number of the first byte is zero, etc. */
 	protected int lastByteAcked = -1;

 	/** Pointer to the last byte sent {@link #lastByteSent}
 	 * at the time when {@link #dupACKthreshold} duplicate acknowledgments
 	 * were received. Only when all the data outstanding at
 	 * that moment are acknowledged will the sender have
 	 * fully recovered from the loss.
 	 * 
 	 * <p>In the worst case scenario, all of the currently
 	 * outstanding data might have been lost and will
 	 * need to be retransmitted. The sender doesn't know
 	 * this, so it keeps track of it.<BR>
 	 * (Recall that a Go-Back-N protocol would
 	 * now re-transmit all outstanding data.)
 	 */
 	protected int lastByteSentBefore3xDupAcksRecvd = -1;

 	/** Sender's current state. */
 	protected SenderState currentState = null;

 	/** Current congestion window size, in bytes.
     * (Note the package visibility, needed for the TCPSenderState object
     * to access and modify this attribute.) */
 	int congWindow = MSS;

 	/** The Slow-Start threshold is a dynamically-set value indicating
	 * an upper bound on the congestion window above which a
	 * TCP sender transitions from Slow-Start to the Congestion Avoidance
	 * state. The default value is 65535 bytes (or 64 KBytes).
 	 * (Note the package visibility of this field.) */
 	int SSThresh = 65535;

 	/** Default value of the timer, in our case equals to {@value} &times; RTT. */
 	static final int TIMER_DEFAULT = 3;

 	/** Retransmission timer (RTO) estimation;
 	 * performed after each new ACK is received.
 	 * @see RTOEstimator */
 	RTOEstimator rtoEstimator = null;

 	/** Retransmission timer (RTO) value, measured in simulator time ticks
 	 * that are defined by {@link Simulator#getTimeIncrement()}.</p>
 	 * 
 	 * <p>The timer is activated when a new segment is transmitted.
 	 * When all outstanding segments are acknowledged, the timer is
 	 * deactivated.  When a <i>regular</i> acknowledgment is received
 	 * <b>and</b> there are still outstanding, non-acknowledged segments,
 	 * the timer should be <b>re-started</b>. */
 	TimerSimulated rtoTimer = null;

 	/** Handle to a running RTO timer ({@link #rtoTimer}), so it can be canceled, if needed. */
 	protected TimerSimulated rtoTimerHandle = null;

 	/**
 	 * This is a TCP connection "inactivity-timeout" timer.
 	 * Both <a href="http://www.apps.ietf.org/rfc/rfc2581.html" target="page">RFC 2581</a>
	 * and <a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>
	 * in Section&nbsp;4.1: <em>Restarting Idle Connections</em>, state that
	 * the TCP sender should begin in <em>slow start</em> if it has not sent
	 * data in an interval exceeding the retransmission timeout
	 * ({@link #rtoTimer}).</p>
	 * 
	 * <p> Check also <a href="http://www.isi.edu/touch/pubs/draft-hughes-restart-00.txt" target="page">Issues
	 * in TCP Slow-Start Restart After Idle</a>.</p>
 	 */
 	TimerSimulated idleConnectionTimer = null;

 	/** Handle to a running "idle-connection" timer ({@link #idleConnectionTimer}),
 	 * so it can be canceled, if needed. */
 	protected TimerSimulated idleConnectionTimerHandle = null;

 	/** The threshold number of duplicate acknowledgments after which
 	 * the TCP sender will assume that the oldest outstanding segment
 	 * is lost and perform <em>Fast Retransmit</em>.
 	 * Usually set to {@value}.</p>
 	 * <p>Note that because the threshold value is usually 3,
 	 * all related variables are named with <em>3dupAcks</em>,
 	 * such as {@link #lastByteSentBefore3xDupAcksRecvd} or
 	 * the method {@link #onThreeDuplicateACKs()}. */
 	protected static final int dupACKthreshold = 3;

 	/** Counter of duplicate acknowledgments.
 	 * If the counter reaches three (3) or more dup-ACKs, the sender assumes
 	 * that the oldest unacknowledged segment is lost and needs to be retransmitted. */
 	protected int dupACKcount = 0;

 	/** Last advertised size of the currently available space in the receiver's buffer. */
 	protected int rcvWindow = 65536;	// assume default as 65536 bytes

 	/**
 	 * Base class constructor; not public.
 	 */
 	protected Sender(Endpoint localTCPendpoint_) {
		this.localEndpoint = localTCPendpoint_;

		// Initialize the buffer stream; to be grown as needed
		bytestream = ByteBuffer.allocate(MSS);

		// start the retransmission timeout (RTO) estimation
		rtoEstimator = new RTOEstimator(
			localEndpoint.getSimulator().getTimeIncrement()
		);

		// create the RTO timer but do not start it up
		// because initially there are no outstanding segments
		rtoTimer = new TimerSimulated(
			this, 1 /* type equals "1" */, 0.0 /* inactive */
		);

		// create the inactivity timer, to be started
		// if the sender becomes idle
		idleConnectionTimer = new TimerSimulated(
			this, 2 /* type equals "2" */, 0.0 /* inactive */
		);
 	}
 
	/**
	 * Callback method to call when a simulated timer expires. <BR>
	 * Currently, TCP Sender sets two types of timers:<BR>
	 * &bull; retransmission (RTO) timer ({@link #rtoTimer}), set as type "<tt>1</tt>"<BR>
	 * &bull; inactivity-timeout timer ({@link #idleConnectionTimer}), set as type "<tt>2</tt>"
	 * </p>
	 * 
	 * <p>In case of an expired RTO timer, this method will ask
	 * the current state object to handle the expired-timer event
	 * which, in turn, will call {@link #onExpiredRTOtimer}
	 * 
	 * @param timerType_ the type of the timer that expired ({@link #rtoTimer} or {@link #idleConnectionTimer})
	 * @see TimedComponent
	 */
	@Override
	public void timerExpired(int timerType_) {
		if (timerType_ == 1) {
			if (
				(Simulator.currentReportingLevel  & Simulator.REPORTING_SENDERS) != 0
			) {
				System.out.println(" ***** RTO timer timeout! *****");
			}
	
			// If RTO timeout occurred, handle it:
			rtoTimerHandle = null;	// clear the timer handle
			// Send out the oldest unacknowledged segment, assuming that
			// all TCP senders react in the same way to an RTO timeout
	
			// The oldest unacknowledged segment will be sent from the current state object
			currentState = currentState.handleRTOtimeout(
				getOldestUnacknowledgedSegment()
			);
		} else if (timerType_ == 2) {
			if (
				(Simulator.currentReportingLevel  & Simulator.REPORTING_SENDERS) != 0
			) {
				System.out.println(" %%%%% Idle-connection timer timeout! %%%%%");
			}

			// If idle-connection timeout occurred, handle it:
			idleConnectionTimerHandle = null;	// clear the timer handle
			// Reset the sender to begin in the slow-start state
			resetParametersToSlowStart();
			currentState = currentState.slowStartState;
		}
	} //TODO: Else ????

	/**
	 * Helper method, called from derived classes to start up
	 * the retransmission (RTO) timer countdown.
	 * @see #rtoTimer
	 */
	void startRTOtimer() {
		// if this timer is already running, cancel it first
		if (rtoTimerHandle != null) {
			cancelRTOtimer();
		}

		// Set the future time to fire the RTO timer.
		rtoTimer.setTime(localEndpoint.getSimulator().getCurrentTime() + rtoEstimator.getTimeoutInterval());
		try {
			rtoTimerHandle = localEndpoint.getSimulator().setTimeoutAt(rtoTimer);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (
			(Simulator.currentReportingLevel  & Simulator.REPORTING_SENDERS) != 0
		) {
			System.out.println(
				"\t^^^^^^^ RTO Timer started to fire at the _start_ of RTT #" + (int)rtoTimer.getTime()
			);
		}
	}

	/**
	 * Helper method, called from derived classes to cancel
	 * the retransmission (RTO) timer when there are no
	 * more unacknowledged segments.
	 */
	void cancelRTOtimer() {
		if (rtoTimerHandle != null) {
			try {
				localEndpoint.getSimulator().cancelTimeout(rtoTimerHandle);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			rtoTimerHandle = null;	// clear the timer handle
		}
	}

	/**
	 * Helper method to start up the idle-connection timer countdown.
	 * This timer must be started only if <em>zero</em> data
	 * is left to send <em>and</em> the sender has received
	 * acknowledgment for all outstanding data.
	 * @see #idleConnectionTimer
	 */
	void startIdleConnectionTimer() {
		// Do nothing if this timer is already running
		// or the sender is still not done:
		if (
			(idleConnectionTimerHandle != null) ||
			(lastByteAcked < lastByteSent)
		) {
			return;
		}

		// Set the future time to fire the inactivity-timeout timer.
		idleConnectionTimer.setTime(localEndpoint.getSimulator().getCurrentTime() + rtoEstimator.getTimeoutInterval());
		try {
			idleConnectionTimerHandle =
				localEndpoint.getSimulator().setTimeoutAt(idleConnectionTimer);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Helper method, called on the expired retransmission timeout (RTO) timer
	 * from the sender's current state object  {@link SenderState#handleRTOtimeout}.
	 * Works slightly differently for different types of TCP senders (Tahoe, Reno, etc.).
	 * 
	 *  @see SenderState
	 */
	abstract void onExpiredRTOtimer();

	/**
	 * Helper method, called on threshold number of duplicate ACKs.
	 * Works differently for different types of TCP senders (Tahoe, Reno, etc.).<BR>
	 * Note that the threshold number can be modified, see the
	 * attribute {@link #dupACKthreshold}, but this method is named
	 * after the commonly used value of <tt>3</tt> dupACKs.
	 * 
	 *   @see SenderState
	 */
	abstract void onThreeDuplicateACKs();

	/**
	 * Accessor for retrieving the statistics of the total number
	 * of bytes <i>successfully</i> transmitted so far during this
 	 * simulation.  This value is used for statistics gathering and
 	 * reporting purposes.
 	 * 
	 * @return Returns the cumulative number of bytes <i>successfully</i> transmitted thus far.
	 */
	public int getTotalBytesTransmitted() {
		// NOTE: This assumes that the very first byte in the entire session
		// had the sequence number assigned as equal to _zero_ !!
		return (lastByteAcked + 1);
	}

	/**
	 * Helper method to extract the oldest unacknowledged segment
	 * from the input bytestream.
	 * @return Returns the oldest currently unacknowledged segment.
	 */
	Segment getOldestUnacknowledgedSegment() {
		// Extract the oldest segment segment from the input bytestream
		int currentPosition_ = bytestream.position();
		// Shouldn't the current position be <code>lastByteSent</code> ?!?!?

		// bring the buffer position backward to the "last byte acknowledged"
		bytestream.position(currentPosition_ - (lastByteSent - lastByteAcked));

		byte[] segment_ = new byte[MSS];
		bytestream.get(segment_, 0, segment_.length);

		// bring the buffer position forward to the "last byte sent"
		bytestream.position(currentPosition_);

		// The oldest unacknowledged segment will be sent from the current state object
		return new Segment(
			localEndpoint.getRemoteTCPendpoint(),
			localEndpoint.getLocalRcvWindow(), lastByteAcked + 1, segment_
		);
	}

	/**
 	 * "Sends" segments by passing them to the network layer protocol object.
 	 * Note that TCP sender operates the same <code>send()</code>
 	 * regardless of its current state. The sender state is used
 	 * in {@link #handle(Segment)} to process the acknowledgment
 	 * segments from the receiver. During ACK processing, the
 	 * sending parameters will be set, that are used in this
 	 * <code>send()</code> method.
 	 * 
 	 * @param newData_ The new message to send
 	 */
 	public void send(byte[] newData_) {
 		if (newData_ == null && !bytestream.hasRemaining()) {
 			System.out.println("tcp.Sender.send():  Input bytestream empty -- nothing left to send");

 			startIdleConnectionTimer();
 			return;		// Bail out -- there is NO data to send ...
 		}				// ... so, wait until the next invocation

 		// Enlarge the existing bytestream (if any) and add to it the new data
 		if (newData_ != null) {
 			// Cancel the inactivity-timeout timer if it's running:
 			if (idleConnectionTimerHandle != null) {
 				try {
 					localEndpoint.getSimulator().cancelTimeout(idleConnectionTimerHandle);
 				} catch (Exception ex) {
 					ex.printStackTrace();
 				}
 				idleConnectionTimerHandle = null;	// clear the timer handle
 			}

 			byte[] previousData_ = new byte[bytestream.remaining()];
 			bytestream.get(previousData_);	// Temporarily store the previous bytestream

 			// allocate new buffer space
 			bytestream = ByteBuffer.allocate(previousData_.length + newData_.length);
 			bytestream.put(previousData_);	// Put back the old data at the beginning
 			bytestream.put(newData_);		// Append the new data (after the old data)
 			bytestream.rewind();			// Rewind the buffer
 		}

 		if (bytestream.remaining() < MSS) {
 	 		// NOTE: we start up the inactivity-timeout timer
 	 		// *only* if *zero* bytes are remaining, not here!!

 			System.out.println("tcp.Sender.send():  Insufficient data to send");
 			return;		// Bail out -- there isn't enough data to send a full-size segment
 		}				// ... so, wait until the next invocation

		// Calculate the sending parameters:
 		// 1. How many segments are currently unacknowledged
		int flightSize_ = lastByteSent - lastByteAcked;
		// 2. How many segments can still be sent (a.k.a. "usable window")
		int effectiveWindow_ =
			Math.min(congWindow, rcvWindow) - flightSize_;
		if (effectiveWindow_ < 0) {
			effectiveWindow_ = 0;
		}
		// Display the relevant parameters for congestion control.
		if (		// Debugging reporting:
			(Simulator.currentReportingLevel  & Simulator.REPORTING_SENDERS) != 0
		) {
			System.out.println(
				"SENDER:\t\tCongWin="+congWindow + "\t" + "EffectiveWin="+effectiveWindow_ +
				"\t" + "FlightSize="+flightSize_ + "\t" + "SSThresh="+SSThresh
			);
		} else {	// Default reporting, always printed out:
			System.out.println(
				congWindow + "\t\t" + effectiveWindow_ +
				"\t\t" + flightSize_ + "\t\t" + SSThresh
			);
		}

		// Send only integer multiples of MSS segments,
		// i.e., Nagle's algorithm is not employed here.
		// See: http://en.wikipedia.org/wiki/Nagle_algorithm
		//
		// Of course, we also need to check if there is any data left in the bytestream to send
		int burst_size_ = Math.min(
			effectiveWindow_ / MSS, bytestream.remaining() / MSS
		);

		if (burst_size_ > 0) {
			// Send the "burst_size_" worth of segments:
			for (int seg_ = 0; seg_ < burst_size_; seg_++) {
				// Extract one segment of data from the input bytestream
				byte[] segPayload_ = new byte[MSS];
				bytestream.get(segPayload_, 0, segPayload_.length);

				Segment segment_ = new Segment(
					localEndpoint.getRemoteTCPendpoint(),
					localEndpoint.getLocalRcvWindow(), lastByteSent + 1, segPayload_
				);
				// set the sending time
				segment_.timestamp = (int) localEndpoint.getSimulator().getCurrentTime();

				// Hand the new segment down to the network layer for transmission
				localEndpoint.getNetworkLayerProtocol().send(localEndpoint, segment_);
				lastByteSent += MSS;
			}

			// Start the RTO timer for the just-transmitted segments.
			startRTOtimer();
		} // else send nothing
 	}

	/**
 	 * Processes ACKs received from the receiver.
 	 * Checks for duplicate ACKs and dispatches them
 	 * differently for processing.<BR>
 	 * If a new ACK is received (acknowledging previously
 	 * unacknowledged data), the sender's window may have
	 * opened to send some more segments, so this method
	 * will call {@link #send(byte[])}.
	 *
 	 * @param ack_ An acknowledgment received from the receiver.
 	 */
 	public void handle(Segment ack_) {
		// Update the advertised receive window size
		rcvWindow = ack_.rcvWindow;

		// Is this a newly acknowledged segment (i.e., not a duplicate ACK)?
		if (ack_.ackSequenceNumber > (lastByteAcked + 1)) {
			// Let the current state process a "new" acknowledgment
			currentState = currentState.handleNewACK(ack_);

	    	// If all outstanding data at "dupACKthreshold" dupACKs have been ACK-ed,
	    	// then reset the indicator parameter.
	    	if (lastByteSentBefore3xDupAcksRecvd <= lastByteAcked) {
	    		lastByteSentBefore3xDupAcksRecvd = -1;
	    	}
		} else {	// duplicate ACK
			// Let the current state process a "duplicate" acknowledgment
			try {
				currentState = currentState.handleDupACK(ack_);
			} catch (Exception ex_) {
				System.out.println("tcp.Sender.handle(): " + ex_.toString());
			}
		}
 	}

	/** This method provides a single location to reset
	 * the congestion parameters when the sender needs
	 * to transition to the slow start state from another state.</p>
	 * 
	 * <p>Note that this method does not modify the
	 * current value of {@link #SSThresh}.
	 */
	public void resetParametersToSlowStart() {
		// Set new congestion window = 1 x MSS (single segment)
		congWindow = MSS;

		// Reset also the global counter of duplicate ACKs.
	    dupACKcount = 0;

		// Reset this param as well, just in case...
		lastByteSentBefore3xDupAcksRecvd = -1;
	}
}