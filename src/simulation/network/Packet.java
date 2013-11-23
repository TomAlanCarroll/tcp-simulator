/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation.network;

import simulation.network.NetworkElement;

/**
 * Data packet class.
 * 
 * @author Ivan Marsic
 */
public class Packet implements Cloneable {
	/**
	 * Destination address, to which this packet is sent.
	 */
	public NetworkElement destinationAddr = null;

	/**
	 * The data payload carried in this packet, if any.
	 */
	public byte[] dataPayload = null;

	/** Packet length [in bytes]. */
	public int length = 0;

	/** Indicates whether this packet is corrupted by an error.
	 * This is invented in lieu of building a full-fledged
	 * error-checking mechanism.  If a router or channel wants
	 * to damage this packet, it just sets the flag to <code>true</code>.*/
	public boolean inError = false;

	/**
	 * Packet identifier for reporting/debugging purposes.
	 */
	protected String identifier = "Undefined packet";

	/**
	 * Constructor.
	 * @param destinationAddr_ the destination address to which this packet is sent
	 * @param dataPayload_ the data payload to be carried by this packet, if any
	 */
	public Packet(NetworkElement destinationAddr_, byte[] dataPayload_) {
		this.destinationAddr = destinationAddr_;
		this.dataPayload = dataPayload_;
		this.length = (dataPayload_ != null) ? dataPayload_.length : 0;
	}

	/**
	 * Makes a clone object of this data packet.<BR>
	 * This method is part of the java.lang.Cloneable interface.
	 */
	@Override
	public Object clone() {
        try {
            return super.clone();
        } catch(CloneNotSupportedException ex) {
        	System.out.print("TCPSegment.clone():\t" + ex.toString());
            return null;
        }
    }

	/**
	 * Prints out some basic information about this TCP segment.
	 * It is used mostly for reporting purposes.<BR>
	 * This method is part of the java.lang.Object interface.
	 */
	@Override
	public String toString() {
		return identifier;
	}
}