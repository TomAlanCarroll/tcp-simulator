
/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2012 Rutgers University
 */
package simulation;

import java.util.ArrayList;

/**
 * A full-duplex communication link that connects two network nodes.
 * Packets that are fed on one end will come out at the other end
 * of the link, after appropriate delays.
 * The link has associated transmission and propagation times.
 * 
 * @author Ivan Marsic
 * @see NetworkElement
 */
public class Link extends NetworkElement {
	/**
	 * Transmission time for this communication link
	 * (per packet, assuming all packets are of the same size!).
	 * The time is measured in <em>ticks</em> of the simulator clock.
	 */
	protected double transmissionTime = 0.0;

	/**
	 * Propagation time for this communication link.
	 * Measured in <em>ticks</em> of the simulator clock.
	 */
	protected double propagationTime = 0.0;

	/**
	 * Nodes {@link #node1} and {@link #node2} connected by this link.
	 */
	protected NetworkElement node1 = null;
	protected NetworkElement node2 = null;

	/**
	 * Container for packets in transit from {@link #node1} to {@link #node2}.<BR>
	 * We never check whether this array overflows, because we
	 * hope there will be never more than 100 packets in flight on a link?!?
	 */
	protected ArrayList<Packet> packetsFromN1toN2 = new ArrayList<Packet>(100);

	/**
	 * Delay times for packets stored in the list {@link #packetsFromN1toN2},
	 * in simulation-clock ticks. The delay for each packet is calculated
	 * when the packet is received in {@link #send(NetworkElement, Packet)}
	 * and the delay is decremented in {@link #process(int)} until
	 * it reaches zero. At this time, the packet is delivered to
	 * {@link #node2}.
	 */
	protected double[] packetDelaysN1toN2 = new double[100];

	/**
	 * Container for packets in transit from {@link #node2} to {@link #node1}.<BR>
	 * Similar to {@link #packetsFromN1toN2}.
	 */
	protected ArrayList<Packet> packetsFromN2toN1 = new ArrayList<Packet>(100);

	/**
	 * Similar to {@link #packetDelaysN1toN2}.
	 */
	protected double[] packetDelaysN2toN1 = new double[100];

	/**
	 * Parameters that override {@link NetworkElement#lastTimeProcessCalled}
	 * because Link has different modes of processing.
	 */
	protected double lastTimeProcessCalledMode1 = 0.0;
	protected double lastTimeProcessCalledMode2 = 0.0;

	/**
	 * Constructor.
	 * @param simulator_ the runtime environment
	 * @param name_ the name given to this link
	 * @param node1_ one node to be connected with this link
	 * @param node2_ the other node to be connected with this link
	 */
	public Link(Simulator simulator_, String name_, NetworkElement node1_, NetworkElement node2_) {
		this(simulator_, name_, node1_, node2_, 0.0, 0.0);
	}

	/**
	 * Constructor.
	 * @param simulator_ the runtime environment
	 * @param name_ the name given to this link
	 * @param node1_ one node to be connected with this link
	 * @param node2_ the other node to be connected with this link
	 * @param transmissionTime_ the transmission time associated with this link (measured in clock ticks)
	 * @param propagationTime_ the propagation time associated with this link (measured in clock ticks)
	 */
	public Link(
		Simulator simulator_, String name_, NetworkElement node1_, NetworkElement node2_,
		double transmissionTime_, double propagationTime_
	) {
		super(simulator_, name_);
		//TODO: check that the network elements are not links, because
		// we don't want to directly connect a link to another link
		this.node1 = node1_;
		this.node2 = node2_;
		this.transmissionTime = transmissionTime_;
		this.propagationTime = propagationTime_;
	}

	/**
	 * Parameter getter.
	 * @return the transmission time (in simulator clock ticks)
	 */
	public double getTransmissionTime() {
		return transmissionTime;
	}
	/**
	 * Parameter setter.
	 * @param transmissionTime_ the transmission time to set (in simulator clock ticks)
	 */
	public void setTransmissionTime(double transmissionTime_) {
		this.transmissionTime = transmissionTime_;
	}

	/**
	 * Parameter getter.
	 * @return the propagation time (in simulator clock ticks)
	 */
	public double getPropagationTime() {
		return propagationTime;
	}
	/**
	 * Parameter setter.
	 * @param propagationTime_ the propagation time to set (in simulator clock ticks)
	 */
	public void setPropagationTime(double propagationTime_) {
		this.propagationTime = propagationTime_;
	}

	/**
	 * The link just accepts any new packets given to it
	 * and enqueues the new packet behind any existing packets.
	 * These packets in transit/flight will be delivered on the
	 * other end of the link after appropriate delays,
	 * when method {@link #process(int)} is called.<BR>
	 * Parameter <code>source_</code> is used to
	 * distinguish the nodes connected to the link's ends.</p>
	 * 
	 * <p>At the time when a packet is enqueued, its delay is also
	 * calculated and stored in the corresponding array
	 * ({@link #packetDelaysN1toN2} or {@link #packetDelaysN2toN1}).</p>
	 * 
	 * <p>Note: In the current implementation when calculating the
	 * packet delay, we do not check the packet length.
	 * The transmission time {@link #transmissionTime}
	 * is assumed to be the same for all packets. Of course, this is not true
	 * because TCP acknowledgment-only segments are much shorter than
	 * TCP segments carrying data. this is a TODO item.</p>
	 * 
	 * @param source_ the source of the packet
	 * @param packet_ the new packet in flight on this link
	 * 
	 * @see simulation.NetworkElement#send(NetworkElement, Packet)
	 */
	@Override
	public void send(NetworkElement source_, Packet packet_) {
		// Simply enqueue the new packet behind any existing packets.
		//TODO: should check that the arrays do not overflow!
		if (node1.equals(source_)) { // packet from Node 1 to Node 2
			enqueueNewPacket(packetsFromN1toN2, packetDelaysN1toN2, packet_);
		} else if (node2.equals(source_)) { // packet from Node 2 to Node 1
			enqueueNewPacket(packetsFromN2toN1, packetDelaysN2toN1, packet_);
		} else {
			System.out.println("Link.send() --- PANIC --- impossible packet source!?");
		}
		// This reporting is for debugging purposes only:
		if (
			(Simulator.currentReportingLevel & Simulator.REPORTING_LINKS) != 0
		) {
			System.out.println(
				"\t " + packet_.toString() +
				" received by " + name + " from " + source_.getName()
			);
		}
	}

	/**
	 * This method should be called to signal the passage of time.
	 * The link will deliver appropriate number of packets,
	 * if any, at the other end (opposite from where the
	 * packet was received).
	 * 
	 * @param mode_ the transmission mode for this link; value "0" means both-way transmission,
	 * value "1" means one-way transmission from {@link #node1} to {@link #node2}, and
	 * value "2" means one-way transmission from {@link #node2} to {@link #node1}
	 * @see simulation.NetworkElement#process(int)
	 */
	@Override
	public void process(int mode_) {
		switch (mode_) {
			case 0:
				if (!packetsFromN1toN2.isEmpty()) {
					deliverArrivedPackets(packetsFromN1toN2, packetDelaysN1toN2, node2, lastTimeProcessCalled);
				}

				if (!packetsFromN2toN1.isEmpty()) {
					deliverArrivedPackets(packetsFromN2toN1, packetDelaysN2toN1, node1, lastTimeProcessCalled);
				}
				// Update the last time this method was called, for future reference
				lastTimeProcessCalled = getSimulator().getCurrentTime();
				break;
			case 1:
				if (!packetsFromN1toN2.isEmpty()) {
					deliverArrivedPackets(packetsFromN1toN2, packetDelaysN1toN2, node2, lastTimeProcessCalledMode1);
				}
				lastTimeProcessCalledMode1 = getSimulator().getCurrentTime();
				break;
			case 2:
				if (!packetsFromN2toN1.isEmpty()) {
					deliverArrivedPackets(packetsFromN2toN1, packetDelaysN2toN1, node1, lastTimeProcessCalledMode2);
				}
				lastTimeProcessCalledMode2 = getSimulator().getCurrentTime();
				break;
		}
	}

	/**
	 * Link does not "<em>handle</em>" incoming
	 * packets, so this method does nothing.
	 * 
	 * @param dummySource_ this dummy parameter is <b><em>ignored</em></b>
	 * @param dummyPacket_ this dummy parameter is <b><em>ignored</em></b>
	 * 
	 * @see simulation.NetworkElement#handle(NetworkElement, simulation.Packet)
	 */
	@Override
	public void handle(NetworkElement dummySource_, Packet dummyPacket_) {
		System.out.println("Link.handle():  PANIC -- how did we get here ?!?!?");
	}

	/**
	 * Helper method to enqueue a new packet into one
	 * of the lists and calculate its delay on the link.
	 * 
	 * @param packets_ the list of packets
	 * @param packetDelays_ the array of delays associated with packets
	 * @param packet_ the new packet to enqueue
	 */
	protected void enqueueNewPacket(
		ArrayList<Packet> packets_, double[] packetDelays_, Packet packet_
	) {
		int idx_ = packets_.size();
		packets_.add(packet_);

		if ((idx_ == 0) || (packetDelays_[idx_-1] < propagationTime + transmissionTime) ) {
			// This is the head-of-the-list _OR_
			// the packet before this last packet has already
			// propagated somewhat through the link
			// so this packet's delay will be as if it's the head-of-the-line;
			// although the previous pkt might have propagated just a little,
			// this coarse graining is a good enough approximation ...
			packetDelays_[idx_] = propagationTime + transmissionTime;
		} else {
			// This case should not be possible of a physical link, but
			// we use this object also as "link-layer protocol module" ...
			packetDelays_[idx_] = packetDelays_[idx_-1];
		}
	}

	/**
	 * Helper method to deliver the packets that propagated
	 * through the link and arrived to the other end, if any.<BR>
	 * Note that we assume that the packets are enqueued on the
	 * first-come-first-served basis, so their delays are
	 * sorted in an ascending order.
	 * 
	 * @param packets_ the list of packets
	 * @param packetDelays_ the array of delays associated with packets
	 * @param node_ the node to which the arrived packets will be delivered
	 * @param lastTimeProcessCalled_ 
	 */
	protected void deliverArrivedPackets(
		ArrayList<Packet> packets_, double[] packetDelays_,
		NetworkElement node_, double lastTimeProcessCalled_
	) {
		int lastRemovedIdx_ = 0;
		int lastIdx_ = packets_.size();
		for (int idx_ = 0; idx_ < lastIdx_; idx_++) {
			// decrement the delay by the amount of time elapsed since the last call
			packetDelays_[idx_] -= (getSimulator().getCurrentTime() - lastTimeProcessCalled_);
			if (packetDelays_[idx_] <= 0.0) { // if the delay completely elapsed,
				// deliver this packet to the receiving node
				//TODO: There may be a problem of concurrent access to the "packets_" list,
				// because the called component may call back send()/enqueueNewPacket()
				// to insert new items into the same list.
				node_.handle(this, packets_.remove(0));
				lastRemovedIdx_++;
			}
		}

		// If some packets remained in the list, shift their delays
		// to match the corresponding indices
		if (packets_.size() > 0) {
			lastIdx_ = lastRemovedIdx_ + packets_.size();
			int newIdx_ = 0;
			for (int idx_ = lastRemovedIdx_; idx_ < lastIdx_; idx_++, newIdx_++) {
				// move the old corresponding delay to a new location
				packetDelays_[newIdx_] = packetDelays_[idx_];
				packetDelays_[idx_] = 0.0;	// reset the old corresponding delay
			}
		}
	}
}