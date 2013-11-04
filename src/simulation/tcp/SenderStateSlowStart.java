/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */

package simulation.tcp;

import simulation.Simulator;

/**
 * This class defines how a TCP sender behaves in the slow start state.
 * 
 * @author Ivan Marsic
 *
 */
public class SenderStateSlowStart extends SenderState {

	/**
     * Constructor for the slow start state of a TCP sender.
     * 
     * @param sender
     * @param congestionAvoidanceState
     * @param after3xDupACKstate
     */
    public SenderStateSlowStart(
    	Sender sender, SenderState congestionAvoidanceState, SenderState after3xDupACKstate
    ) {
    	this.sender = sender;
    	this.slowStartState = this;	// itself the slow start state
    	this.congestionAvoidanceState = congestionAvoidanceState;
    	this.after3xDupACKstate = after3xDupACKstate;
    }

	/**
	 * The reason for this method is that the constructors TCPSenderStateSlowStart
	 * and TCPSenderStateCongestionAvoidance need each other, so one has to be
	 * created first, and then the other will be set using this method.<BR>
	 * Thus package visibility only.
	 * 
	 * @param congestionAvoidanceState The congestion avoidance state to set
	 */
	void setCongestionAvoidanceState(SenderState congestionAvoidanceState) {
		this.congestionAvoidanceState = congestionAvoidanceState;
	}

    /**
     * Same as for {@link #setCongestionAvoidanceState(SenderState)}
	 * @param after3xDupACKstate the after3xDupACKstate to set
	 */
	void setAfter3xDupACKstate(SenderState after3xDupACKstate) {
		this.after3xDupACKstate = after3xDupACKstate;
	}

	/**
	 * Helper method to calculate the new value of the congestion
	 * window after a "new ACK" is received that acknowledges
	 * data never acknowledged before.</p>
	 * 
	 * <p>During recovery from a segment loss, the sender limits the number of
	 * segments sent in response to each ACK to two segments during slow-start.
	 * Therefore, cumulative ACKs for segments sent before the loss was
	 * detected count the same as individual ACKs towards increasing CongWin.
	 * (The limit during Reno-style fast recovery is one segment,
	 * {@link SenderStateFastRecovery#calcCongWinAfterNewAck(int, int)}).</p>
	 * 
	 * @param ackSequenceNumber_ acknowledged data sequence number
	 * @param lastByteAcked_ last byte previously acknowledged
	 * @return the new value of the congestion window
	 */
	@Override
	protected int calcCongWinAfterNewAck(
		int ackSequenceNumber_, int lastByteAcked_
	) {
		if (sender.lastByteSentBefore3xDupAcksRecvd == -1) {
    		// This is the initial slow start:
			// Grow the CongWin by the full amount of the (possibly cumulative) ACK
			return sender.congWindow + (ackSequenceNumber_ - lastByteAcked_ - 1);
    	} else {
    		// This is a slow start recovering after a segment loss
    		// and before the sender has acknowledged all the segments
    		// that were outstanding at the time 3x dupACKs were received,
    		// the sender counts cumulative ACKs as worth a single MSS.
    		return sender.congWindow + Sender.MSS;
    	}
	}

	/**
	 * Helper method to look-up the next state
	 * that the sender will transition to after this one.
	 * 
	 * @return the next state to transition to.
	 */
	@Override
	protected SenderState lookupNextStateAfterNewAck() {
    	// Check if the congestion window exceeded the slow-start-threshold;
		// If YES, change the sender's mode to "congestion avoidance"
    	if (sender.congWindow < sender.SSThresh) {
    		return this;	// remain in the slow start state
    	} else {
    		if (
    			(Simulator.currentReportingLevel & Simulator.REPORTING_SENDERS) != 0
			) {
				System.out.println("############## Sender entering congestion avoidance.");
			}
    		// transition to the congestion avoidance state
    		return congestionAvoidanceState;
    	}
	}
}