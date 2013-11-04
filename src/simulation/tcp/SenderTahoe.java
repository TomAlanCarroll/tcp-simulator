/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */
package simulation.tcp;

import simulation.Endpoint;

/**
 * TCP Tahoe implementation of a sender.
 * <P>
 * <b>Note</b>: This implementation is based on
 * <a href="http://www.apps.ietf.org/rfc/rfc1122.html" target="page">RFC 1122 &ndash;
 * Requirements for Internet Hosts -- Communication Layers</a>,
 * published in 1989, which I believe specified TCP Tahoe.
 * See <a href="http://www.apps.ietf.org/rfc/rfc1122.html#sec-4.2" target="page">Section
 * 4.2</a> of RFC&nbsp;1122. <BR>
 * TCP Tahoe was superseded by TCP Reno, specified in
 * <a href="http://www.apps.ietf.org/rfc/rfc2001.html" target="page">RFC 2001</a>
 * and <a href="http://www.apps.ietf.org/rfc/rfc2581.html" target="page">RFC 2581</a>.
 * The current version (&ldquo;TCP NewReno&rdquo;) is specified in
 * <a href="http://tools.ietf.org/html/rfc5681" target="page">RFC 5681</a>.
 * <BR><i>Do not rely on any textbooks for precise details!</i>
 * <BR> Read the textbook(s) for high-level understanding of
 * the material; read the RFCs for precise details.
 * 
 * @author Ivan Marsic
 */
public class SenderTahoe extends Sender {

	/**
	 * Constructor.
	 * @param localTCPendpoint_ The local TCP endpoint
	 * object that contains this module.
	 */
	public SenderTahoe(Endpoint localTCPendpoint_) {
		super(localTCPendpoint_);

		// construct the objects for different states of the sender:
		SenderStateSlowStart slowStartState = new SenderStateSlowStart(
		    this, null, null /* after 3x DupACKs state */
		);
		SenderState congestionAvoidanceState = new SenderStateCongestionAvoidance(
		    this, slowStartState, slowStartState /* after 3x DupACKs state */
		);
		slowStartState.setCongestionAvoidanceState(congestionAvoidanceState);
		// Tahoe goes to slow start after 3x DupACKs:
		slowStartState.setAfter3xDupACKstate(slowStartState);

		// Sender always starts in the "slow start" state
		currentState = slowStartState;
	}

	/**
	 * This method resets the sender's parameters when the
	 * RTO timer timed out.
	 */
	@Override
	void onExpiredRTOtimer() {
		// Reduce the slow start threshold
		// using the old congestion window size.
		SSThresh = congWindow / 2;
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
	 * after 3x dupACKs, it's presumably lost.
	 * 
	 * <p>Tahoe sender doesn't care about the number of
	 * duplicate ACKs as long as it's at least three.
	 * This means that any dupACKs received after the first
	 * three are ignored.
	 * Also, after this kinds of event, the sending mode in
	 * TCP Tahoe is always reset to <i>slow-start</i>.
	 * The method leaves the RTO timer running,
	 * for the outstanding segments.
	 */
	@Override
	void onThreeDuplicateACKs() {
		// Tahoe ignores additional dupACKs over and above the first three.
		if (dupACKcount != dupACKthreshold) return;

		// reduce the slow start threshold
		SSThresh = congWindow / 2;
		// Set to an integer multiple of MSS
		SSThresh -= (SSThresh % MSS);
		SSThresh = Math.max(SSThresh, 2*MSS);

		// Retransmit the oldest unacknowledged (presumably lost) segment.
		// This is called "Fast Retransmit"
		// Recall that Tahoe sender sends only one segment
		// when a loss is detected!
		Segment oldestSegment_ = getOldestUnacknowledgedSegment();
		// the timestamp of retransmitted segments should be set to "-1"
		oldestSegment_.timestamp = -1;
	    localEndpoint.getNetworkLayerProtocol().send(
	    	localEndpoint, oldestSegment_
	    );

		// Reset the congestion parameters
		resetParametersToSlowStart();
	}
}