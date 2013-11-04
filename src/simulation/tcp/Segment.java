/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */
package simulation.tcp;

import simulation.NetworkElement;
import simulation.Packet;

/**
 * TCP segment, which could carry either data, ACK, or both.<BR>
 * Note that this class implements java.lang.Comparable so that
 * TCP segments can be compared (and sorted) by their sequence
 * number.
 * 
 * @author Ivan Marsic
 */
public class Segment extends Packet implements Comparable<Segment> {

	/** Sequence number of this segment, which is the sequence
	 * number of the <i>first byte</i> of data carried in this
	 * segment. */
	public int dataSequenceNumber = 0;

	/** Acknowledgment sequence number, in case this segment
	 * also acknowledges received data. This number is valid
	 * only if the <code>isAck</code> flag is set {@link #isAck}.
	 */
	public int ackSequenceNumber = 0;

	/** A flag that informs whether or not this segment contains an ACK. */
	public boolean isAck;

	/** The size of the currently available space in the receiver's buffer
	 * (used mostly for buffering out-of-order segments).
	 * @see Receiver#rcvBuffer
	 */
	public int rcvWindow = 0;
	
	/** The sending time of a segment (similar to the timestamp option in
	 * the <em>Options</em> field of an actual TCP header).
	 * See <a href="http://www.ietf.org/rfc/rfc1323.txt" target="page">RFC 1323,
	 * Section 3.2 TCP Timestamps Option</a>.<BR>
	 * The corresponding acknowledgment segment should bounce the same value.</p>
	 * 
	 * <p>In fact, there should be two timestamps per segment, if acknowledgments
	 * are piggybacked on data segments, but in this simulator we assume
	 * that all acknowledgments are zero-data segments (i.e., they don't have
	 * any payload. </p>
	 * 
	 * <p>This field should be set to <code>-1</code> if the segment is 
	 * a retransmitted segment, and no RTT estimation should be performed 
	 * for retransmitted segments. */
	public int timestamp = -1;

	/** Ordinal number of this segment. This is only for tracking
	 * purposes and this field is <i>not</i> present in actual
	 * TCP segments.<BR>
	 * Note: The value is computed assuming that {@link #dataSequenceNumber}
	 * starts at zero. */
	int ordinalNum;
	/** Similar to {@link #ordinalNum} */
	int ordinalNumAck;

	/**
	 * Constructor for data-only segments.
	 * 
	 * @param rcvWindow_  the current receive window size of the sender of this segment
	 * @param seqNum_ the sequence number for the data payload.
	 * @param dataPayload_ the data payload contained in this segment
	 */
	public Segment(
		NetworkElement destinationAddr_, int rcvWindow_, int seqNum_, byte[] dataPayload_
	) {
		this(destinationAddr_, rcvWindow_, seqNum_, dataPayload_, -1);
	}

	/**
	 * Constructor for acknowledgment-only segments (zero data payload).
	 * @param rcvWindow_ the current receive window size of the sender of this segment
	 * @param ackSeqNum_ the acknowledgment sequence number of this segment
	 */
	public Segment(
		NetworkElement destinationAddr_, int rcvWindow_, int ackSeqNum_
	) {
		this(destinationAddr_, rcvWindow_, -1, null, ackSeqNum_);
	}

	/**
	 * Constructor for both data and acknowledgment segments,
	 * i.e., an acknowledgment is piggybacked on a data segment
	 * going to the same destination.
	 * 
	 * @param rcvWindow_ the current receive window size of the sender of this segment
	 * @param seqNum_ the sequence number of this segment
	 * @param dataPayload_ the data payload, if any
	 * @param ackSeqNum_ the acknowledgment sequence number, if any
	 */
	public Segment(
		NetworkElement destinationAddr_, int rcvWindow_,
		int seqNum_, byte[] dataPayload_, int ackSeqNum_
	) {
		super(destinationAddr_, dataPayload_);
		this.rcvWindow = rcvWindow_;
		this.dataSequenceNumber = seqNum_;
		this.ackSequenceNumber = ackSeqNum_;
		this.isAck = (ackSeqNum_ >= 0);

		//TODO NOTE: This must be corrected because currently we assume that any
		// segments smaller than 1xMSS are 1-byte persist-timer segments.
		// However, this ignores a possibility that Nagle's algorithm is implemented!!
		this.ordinalNum =
				dataSequenceNumber / Sender.MSS +	// how many full MSS segments were created
				dataSequenceNumber % Sender.MSS +	// how many 1-byte segments (for persist timer)
				1;	// add one because this is the ordinal number
		this.ordinalNumAck =
				ackSequenceNumber / Sender.MSS +	// how many full MSS segments were created
				ackSequenceNumber % Sender.MSS +	// how many 1-byte segments (for persist timer)
				1;	// add one because this is the ordinal number
	}

	/**
	 * Prints out some basic information about this TCP segment.
	 * It is used mostly for reporting/debugging purposes.
	 * This method is part of the java.lang.Object interface.
	 */
	@Override
	public String toString() {
		if (isAck) {
			identifier = "ACK # " + Integer.toString(ordinalNumAck);
		}
		// Note that an ACK can be piggybacked on a data segment.
		if (length > 0) {
		identifier =
			"segment # " + Integer.toString(ordinalNum)
//			+ " (" + Integer.toString(length) + ")  "
			;
		}
		return identifier;
	}

	/**
	 * This method is part of the java.lang.Comparable<T> interface.
	 * Lists (and arrays) of objects that implement this interface
	 * can be sorted automatically by <code>java.util.Collections.sort()</code>
	 * ( and <code>java.util.Arrays.sort()</code> ).
	 * 
	 * <P>Such capability is needed by the TCP receiver module when
	 * filling the gaps in the sequence numbers of received segments.
	 * @see Receiver#checkBufferedSegments()
	 */
	@Override
	public int compareTo(Segment anotherSegmentToCompareTo_) {
		if (this.dataSequenceNumber < anotherSegmentToCompareTo_.dataSequenceNumber) {
			// this object is less than the specified object
			return -1;
		} else if (this.dataSequenceNumber > anotherSegmentToCompareTo_.dataSequenceNumber) {
			// this object is greater than the specified object
			return 1;
		} else {
			// this object is equal to the specified object
			return 0;
		}
	}

	/**
	 * Attribute setter for the acknowledgment sequence number.
	 * Defined because {@link Receiver#handle(Segment)}
	 * resets the ACK sequence number for cumulative ACKs,
	 * but then we need to recompute {@link #ordinalNumAck} as well.
	 * @param ackSequenceNumber_ the acknowledgment sequence number to set
	 */
	public void setAckSequenceNumber(int ackSequenceNumber_) {
		this.ackSequenceNumber = ackSequenceNumber_;
		this.ordinalNumAck =
			ackSequenceNumber / Sender.MSS +	// how many full MSS segments were created
			ackSequenceNumber % Sender.MSS +	// how many 1-byte segments (for persist timer)
			1;	// add one because this is the ordinal number
	}
}