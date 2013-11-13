/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation.network;

import simulation.Simulator;

/**
 * The interface for simulated network elements (nodes and links).<BR>
 * Provides two universal methods: {@link #send(NetworkElement, Packet)} for downcall
 * and {@link #handle(NetworkElement, Packet)} for upcall, from components that
 * are above or below this component in the protocol stack.
 * 
 * @author Ivan Marsic
 *
 */
public abstract class NetworkElement {
	/**
	 * Object that provides the runtime environment,
	 * mainly stuff related to the simulation clock,
	 * such as timer management and the reference time.
	 */
	protected Simulator simulator = null;

	/**
	 * The given name of this network element, used
	 * mostly for reporting/debugging purposes.
	 */
	String name = null;

	/**
	 * Indicates when the last time the method {@link #process(int)}
	 * was called, so that the Link knows how much time elapsed
	 * since the last call.
	 */
	protected double lastTimeProcessCalled = 0.0;

	public NetworkElement(Simulator simulator_, String name_) {
		this.simulator = simulator_;
		this.name = name_;
	}

	/**
	 * Attribute getter.
	 * @return the name given to this network element
	 * @see #name
	 */
	public String getName() {
		return name;
	}

	/**
	 * The method used by the simulator to signal the passage of time
	 * to this network element. The element then needs to do
	 * the work appropriate for the amount of time elapsed
	 * since the previous call to this method ({@link #lastTimeProcessCalled}).
	 * 
	 * @param mode_ the processing mode, depends on the actual network element
	 * @see simulation.Simulator#run(java.nio.ByteBuffer, int)
	 */
	public abstract void process(int mode_);

	/**
	 * The method to send data from an upper-layer protocol.<BR>
	 * Note that parameter <code>source_</code> is usually ignored
	 * but {@link simulation.network.Link#send(NetworkElement, Packet)} uses it to
	 * distinguish the nodes connected to its ends.
	 * 
	 * @param source_ the <em>immediate</em> source network element that sends this packet;
	 * note that this may not be the original source that generated this packet,
	 * but rather a router that simply relays someone else's packet
	 * @param packet_ a packet from above to transmit
	 */
	public abstract void send(NetworkElement source_, Packet packet_);

	/**
	 * The method to receive packets from a lower-layer protocol.
	 * @param source_ the <em>immediate</em> source network element that passes this packet;
	 * which may not be the original source that generated this packet
	 * @param packet_ a packet to receive from below and process
	 */
	public abstract void handle(NetworkElement source_, Packet packet_);

	/**
	 * Getter method.
	 * @return the simulator runtime environment
	 */
	public Simulator getSimulator() {
		return simulator;
	}
}