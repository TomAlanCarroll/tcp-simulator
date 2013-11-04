/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation.tcp;

import simulation.Simulator;

/**
 * This abstract class provides the TCP sender state interface.<BR>
 * Example sender states are: "slow start", "congestion avoidance",
 * "fast recovery", etc.
 * The derived classes provide the behavior specific to the given
 * state.</p>
 * 
 * <p>This class implements the
 * <a href="http://en.wikipedia.org/wiki/State_pattern" target="page">State design pattern</a>.</p>
 * 
 * @see SenderStateSlowStart
 * @see SenderStateCongestionAvoidance
 * @see SenderStateFastRecovery
 * @author Ivan Marsic
 *
 */
public abstract class SenderState {
	/** TCP sender.
	 * Represents the context object for this state.
	 * Several attributes (congWindow, SSThresh) of the sender are accessed
	 * from here. */
	protected Sender sender;
	
    protected SenderState slowStartState = null;
    protected SenderState congestionAvoidanceState = null;
    protected SenderState after3xDupACKstate = null;

	/**
	 * Helper method to calculate the new value of the congestion
	 * window after a "new ACK" is received that acknowledges
	 * data never acknowledged before.
	 * 
	 * @param ackSequenceNumber_ acknowledged data sequence number
	 * @param lastByteAcked_ last byte previously acknowledged
	 * @return the new value of the congestion window
	 */
    protected abstract int calcCongWinAfterNewAck(
    	int ackSequenceNumber_, int lastByteAcked_
    );

	/**
	 * Helper method to look-up the next state
	 * that the sender will transition to after it received
	 * a "new ACK".
	 * 
	 * @return the next state to transition to.
	 */
    protected abstract SenderState lookupNextStateAfterNewAck();

	/**
	 * Processes a single new (i.e., <i>not duplicate</i>) acknowledgment
	 * segment in the <i>slow start</i> transmission mode.</p>
	 * 
	 * @param ack_ The current acknowledgment segment, to be processed.
	 * @return Returns the next state to which the TCP sender transitions.
	 */
    public SenderState handleNewACK(Segment ack_) {
    	// Check for a NULL input argument.
    	//TODO: perhaps we should throw an exception here?
    	if (ack_ == null) return this;

		// Every time we receive a new ACK:
    	// Update the running estimate of the RTO timer interval:
    	sender.rtoEstimator.updateRTT(
        	sender.localEndpoint.getSimulator().getCurrentTime(),
        	ack_.timestamp
        );

    	// Update the Last-Byte-Acked param, but remember the previous value
    	int lastByteAckedPrevious = sender.lastByteAcked;
    	sender.lastByteAcked = ack_.ackSequenceNumber - 1;

    	// Update the congestion window size.
    	sender.congWindow =
    		calcCongWinAfterNewAck(ack_.ackSequenceNumber, lastByteAckedPrevious);

    	// Re-start the RTO timer for any outstanding segments.
    	if (sender.lastByteAcked < sender.lastByteSent) {
    		sender.startRTOtimer();
    	} else { // everything is ACK-ed, cancel the RTO timer
    		sender.cancelRTOtimer();
    	}
		// Just in case, also reset the counter of duplicate ACKs.
    	sender.dupACKcount = 0;

    	// return the next state that the sender will transition to
    	return lookupNextStateAfterNewAck();
    }

    /**
     * Counts a duplicate ACK and checks if the count equals 3.
     * If <em>exactly</em> three dupACKs are received, it
     * performs the <em>fast retransmit</em> and updates
     * the congestion parameters.</p>
     * 
     * <p>Tahoe ignores additional dupACKs over and above the first three.
	 * Reno doesn't&mdash;it counts them within its <em>fast recovery</em>
	 * procedure. See {@link SenderStateFastRecovery#handleDupACK(Segment)}.
     */
    public SenderState handleDupACK(Segment dupAck_) {
		// Update the sender's count of duplicate ACKs.
		sender.dupACKcount++;

		// If three duplicate ACKs are received so far, perform "Fast Retransmission"
		// Note: Tahoe ignores additional dupACKs over and above the first three.
		// Reno doesn't ignore -- see TCPSenderStateFastRecovery#handleDupACK()
		if (sender.dupACKcount > 2) {
			if (
				(Simulator.currentReportingLevel  & Simulator.REPORTING_SENDERS) != 0
			) {
				System.out.println(
					" ..... Three (or more) duplicate ACKs received! ....."
				);
			}

			// Perform the necessary actions, depending on the type of
			//   TCP sender (Tahoe, Reno, etc.)
			sender.onThreeDuplicateACKs();

		    // Transition into the state that comes after 3 x duplicate ACKs
		    // depending  on the type of TCP sender (Tahoe, Reno, etc.)
    		if (
    			(Simulator.currentReportingLevel & Simulator.REPORTING_SENDERS) != 0
        		&& after3xDupACKstate instanceof SenderStateFastRecovery
			) {
				System.out.println("############## Sender entering fast recovery.");
			}
	    	return after3xDupACKstate;

		} else {
			// do nothing because we still don't know whether the segment is lost
			return this;	// remain in the slow start state
		}
    }

    /**
	 * Processes the TCP sender reaction to a retransmission timer (RTO) timeout.<BR>
	 * Method called on the expired retransmission timeout (RTO) timer.
	 * After this kind of an event, the next state in any type of
	 * a TCP sender is always reset to <i>slow-start</i>.
	 * 
	 * @param oldestUnackedSeg_ Currently the oldest unacknowledged segment (presumably lost), to be retransmitted.
	 * @return Returns the next state to which the TCP sender transitions.
	 */
    public SenderState handleRTOtimeout(Segment oldestUnackedSeg_) {
		// perform actions specific to the type of TCP sender
    	sender.onExpiredRTOtimer();

		// Retransmit the oldest unacknowledged (presumably lost) segment.
		// Recall that all TCP senders send only one segment when the RTO timer expires.
    	sender.localEndpoint.getNetworkLayerProtocol().send(
    		sender.localEndpoint, oldestUnackedSeg_
    	);

    	// Transition to the slow start state (if this state is not already slow start).
    	return slowStartState;
    }
}