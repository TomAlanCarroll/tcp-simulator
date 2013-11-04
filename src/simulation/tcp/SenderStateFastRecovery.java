/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */

package simulation.tcp;

import simulation.Simulator;

/**
 * TCP Reno sender's state Fast Recovery.
 * (TCP Tahoe does <i>not</i> have this state.)
 * TCP Reno sender remains in the Fast Recovery state
 * until a new ACK acknowledges <i>all</i> the data
 * that were outstanding at the time when {@link Sender#dupACKthreshold}
 * duplicate acknowledgments were received (and the sender entered
 * this state).
 * 
 * @see SenderReno
 * @author Ivan Marsic
 *
 */
public class SenderStateFastRecovery extends SenderState {

	/**
     * Constructor for the fast recovery state of a TCP Reno sender.
     * 
     * @param sender
     * @param slowStartState Slow start state
     * @param congestionAvoidanceState Congestion avoidance state
     */
    public SenderStateFastRecovery(
    	Sender sender, SenderState slowStartState,
    	SenderState congestionAvoidanceState
    ) {
    	this.sender = sender;
    	this.slowStartState = slowStartState;
    	this.congestionAvoidanceState = congestionAvoidanceState;
    }

	/**
	 * Helper method to calculate the new value of the congestion
	 * window after a "new ACK" is received that acknowledges
	 * data never acknowledged before.<BR>
	 * This is where old TCP Reno and TCP NewReno differ.</p>
	 * 
	 * <p><a href="http://tools.ietf.org/html/rfc2581" target="page">RFC 2581</a> for
	 * TCP Reno in Section 3.2 Fast Retransmit/Fast Recovery
	 * in Step 5 says:
	 * &ldquo;<i>When the next ACK arrives that acknowledges new data,
	 * ... this ACK should acknowledge all the intermediate
     * segments sent between the lost segment and the receipt of the
     * third duplicate ACK, if none of these were lost.</i>&rdquo;<BR>
     * But, it does't say what if this is not true.</P>
	 * 
	 * <P>The answer is that the <i>old</i> Reno simply exits
	 * fast recovery when it receives an ACK for previously
	 * unacknowledged data (known as a "recovery ACK"), and that is what
	 * <a href="http://tools.ietf.org/html/rfc2581" target="page">RFC 2581</a>
	 * says about processing new ACKs during Fast Recovery.</p>
     * 
	 * <P>Unlike this, TCP <b>NewReno</b> sender
	 * distinguishes "partial acknowledgments"
	 * as defined in <a href="http://tools.ietf.org/html/rfc3782" target="page">RFC 3782</a>
	 * (ACKs that cover previously unacknowledged data, but
	 * not all the data outstanding when loss was detected). The sender
	 * remains in Fast Recovery until a new ACK acknowledges <i>all</i>
	 * the data outstanding at the time when {@link Sender#dupACKthreshold}
	 * dupACKs were received.<BR>
	 * Only when the NewReno sender receives a "full ACK",
	 * it behaves the same as old Reno, and exits Fast Recovery.<BR>
	 * Whether the new ACK is "partial" or "full" is determined
	 * by comparing it to the parameter
	 * {@link Sender#lastByteSentBefore3xDupAcksRecvd}.</P>
	 * 
	 * @param ackSequenceNumber_ acknowledged data sequence number
	 * @param lastByteAcked_ last byte previously acknowledged (not yet updated with this new ACK!)
	 * @return the new value of the congestion window
	 */
	@Override
	protected int calcCongWinAfterNewAck(
		int ackSequenceNumber_, int lastByteAcked_
	) {
    	// Regular Reno doesn't distinguish "partial" vs. "full"
    	// acknowledgments (see RFC 2582 -- http://tools.ietf.org/html/rfc2582)
    	// so as soon as we get a new ACK, we get out of
    	// the Fast Recovery state (and enter Congestion Avoidance)

    	// Update the congestion window size
		// and check if fast recovery is completed:
    	if (sender.lastByteSentBefore3xDupAcksRecvd == -1) {
    		// PANIC: This is the initial slow start -- how could we be here?!?!?
    		System.out.println(
    			"PANIC in " + this.getClass().getName() + "#" + this.getClass().getEnclosingMethod().getName()
    		);
    		return sender.congWindow;
    	} else if ((sender instanceof SenderNewReno) &&
    		(ackSequenceNumber_ < sender.lastByteSentBefore3xDupAcksRecvd)
    	) {		// "partial ACK" received
    		// ONLY in case of a NewReno sender, because of a "partial ACK":
    		// 1. First, retransmit the first unacknowledged segment
    		sender.localEndpoint.getNetworkLayerProtocol().send(
    			sender.localEndpoint, sender.getOldestUnacknowledgedSegment()
    		);
    		// 2. Second, calculate the new congestion window size
    		int newlyAcked = ackSequenceNumber_ - lastByteAcked_;
    		// 2.a) Deflate the congestion window by the amount of new data acknowledged
    		int congWindowTemp = sender.congWindow - newlyAcked;
    		if (newlyAcked >= Sender.MSS) {
    			// 2.b) If the partial ACK acknowledges at least one MSS of new data
    			// then add back MSS bytes to the congestion window
    			// to reflect the segment that has left the network
    			congWindowTemp += Sender.MSS;
    		}
			return congWindowTemp;
    	} else {	// "full ACK" received
	    	// All outstanding data at 3x dupACKs have been ACK-ed,
	    	// so reset the indicator parameter.
    		sender.lastByteSentBefore3xDupAcksRecvd = -1;

    		// Set the congestion window size to slow start threshold;
    		// this is termed "deflating" the window.
    		return sender.SSThresh;
    	}
	}

	/**
	 * Helper method to return the next state after a "new ACK".
	 * After fast recovery, Reno sender always enters
	 * the congestion avoidance state.<BR>
	 * Note that NewReno distinguishes "partial acknowledgments"
	 * as defined in <a href="http://tools.ietf.org/html/rfc3782" target="page">RFC 3782</a>
	 * (ACKs that cover previously unacknowledged data, but
	 * not all the data outstanding when loss was detected).
	 * 
	 * @return the next state to transition to.
	 */
	@Override
	protected SenderState lookupNextStateAfterNewAck() {
    	// Update the congestion window size
		// and check if fast recovery is completed:
    	if ((sender instanceof SenderNewReno) &&
    		(sender.lastByteAcked < sender.lastByteSentBefore3xDupAcksRecvd)
    	) {					// "partial ACK" received
			return this;	// remain in the fast recovery state   		
    	} else {	// "full ACK" received
	    	// All outstanding data at 3x dupACKs have been ACK-ed,
	    	// so transition to the congestion avoidance state.
			if (	// For debugging purposes only...
	    		(Simulator.currentReportingLevel & Simulator.REPORTING_SENDERS) != 0
			) {
				System.out.println("############## End of Fast Recovery; sender entering Congestion Avoidance.");
			}
	    	return congestionAvoidanceState;
    	}
	}

    /**
     * This method handles a duplicate acknowledgment
     * during <em>fast recovery</em>. All it does is to
     * inflate the congestion window by one <tt>MSS</tt>.<BR>
     * Note that it overrides the base class method
     * {@link SenderState#handleDupACK(Segment)}.
     * 
     * @param dupAck_ The duplicate acknowledgment to process.
     * @return Returns the new state to which the sender will
     * transition after the dupACK event (may be this same state).
     */
	@Override
    public SenderState handleDupACK(Segment dupAck_) {
		// In the fast-recovery state, the TCP Reno sender
    	// does NOT count the number of duplicate ACKs.

		// Increase the congestion window by one full MSS.
		// This inflates the congestion window for the
	    //  additional segment that has left the network.
		sender.congWindow += Sender.MSS;

		return this;	// remain in the fast recovery state
    }
}