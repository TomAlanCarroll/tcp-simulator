/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */
package simulation.tcp;

import simulation.Endpoint;

/**
 * TCP <i>old</i> Reno implementation of a sender that appeared first
 * in early 1990s. TCP Reno followed TCP Tahoe, which was developed
 * in late 1980s.<BR>
 * This class does <i>not</i> implement a TCP NewReno sender.
 * 
 * @see SenderNewReno
 *
 * @author Ivan Marsic
 */
public class SenderReno extends Sender {

	/**
	 * Constructor.
	 * 
	 * @param localTCPendpoint_ The local TCP endpoint
	 * object that contains this module. 
	 */
	public SenderReno(Endpoint localTCPendpoint_) {
		super(localTCPendpoint_);

		// construct the objects for different states of the sender:
		SenderStateSlowStart slowStartState = new SenderStateSlowStart(
		    this, null, null /* after 3x DupACKs state */
		);
		SenderStateCongestionAvoidance congestionAvoidanceState =
			new SenderStateCongestionAvoidance(
				this, slowStartState, null /* after 3x DupACKs state */
			);
		SenderState fastRecoveryState = new SenderStateFastRecovery(
			this, slowStartState, congestionAvoidanceState
		);
		slowStartState.setCongestionAvoidanceState(congestionAvoidanceState);

		// Reno goes to fast recovery after 3x DupACKs:
		slowStartState.setAfter3xDupACKstate(fastRecoveryState);
		congestionAvoidanceState.setAfter3xDupACKstate(fastRecoveryState);

		// Sender always starts in the "slow start" state
		currentState = slowStartState;
	}

	/**
	 * This method resets the sender's parameters when a
	 * RTO timer timed out. It is very similar to
	 * {@link SenderTahoe#onExpiredRTOtimer()}, but only
	 * slightly different in how it calculates <code>SSThresh</code>.
	 */
	@Override
	void onExpiredRTOtimer() {
		// Reduce the slow start threshold using
		// the flight size (this is different from TCP Tahoe!).
		int flightSize_ = lastByteSent - lastByteAcked;
		SSThresh = flightSize_ / 2;
		SSThresh = Math.max(SSThresh, 2*MSS); 			

		// Perform the exponential backoff for the RTO timeout interval 
		rtoEstimator.timerBackoff();
		// and re-start the timer, for the outstanding segments.
		startRTOtimer();

		// Reset the congestion parameters
		resetParametersToSlowStart();
	}

	/**
	 * This method performs the so-called <i>Fast Retransmit</i>
	 * to retransmit the oldest outstanding segment because
	 * after {@link Sender#dupACKthreshold} dupACKs,
	 * it is presumably lost. It is similar to
	 * {@link SenderTahoe#onThreeDuplicateACKs()}, but
	 * different in how it calculates the sender's parameters.
	 * 
	 * <P>Unlike Tahoe, TCP Reno sender considers the number of
	 * duplicate ACKs in excess of the first {@link Sender#dupACKthreshold} dupACKs.
	 * @see SenderStateFastRecovery#handleDupACK(Segment)
	 */
	@Override
	void onThreeDuplicateACKs() {
		// Mark the sequence number of the last currently
		// unacknowledged byte, so that we know when all
		// currently outstanding data will be acknowledged.
		// This ACKis known as a "recovery ACK".
		// This field is used to decide when Fast Recovery should end.
		//@See TCPSenderStateFastRecovery#handleNewACK()
		lastByteSentBefore3xDupAcksRecvd = lastByteSent;

		// reduce the slow start threshold
		int flightSize_ = lastByteSent - lastByteAcked;
		SSThresh = flightSize_ / 2;
		// Set to an integer multiple of MSS
		SSThresh -= (SSThresh % MSS);
		SSThresh = Math.max(SSThresh, 2*MSS);

		// congestion window = 1/2 FlightSize + 3xMSS:
		congWindow =
			Math.max(flightSize_/2, 2*MSS) + 3*MSS;

		// Retransmit the oldest unacknowledged (presumably lost) segment.
		// This is called "Fast Retransmit"
	    Segment oldestSegment_ = getOldestUnacknowledgedSegment();
		// The timestamp of retransmitted segments should be set to "-1"
	    // to avoid performing RTT estimation based on retransmitted segments:
		oldestSegment_.timestamp = -1;
	    localEndpoint.getNetworkLayerProtocol().send(
	    	localEndpoint, oldestSegment_
	    );
	}
}