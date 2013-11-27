/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-2013 Rutgers University
 */

package simulation.network;

import simulation.Simulator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This class is a simple simulation of a network router. It is
 * expressly crafted to "route" TCP packets.  What it does it to
 * enforce that no more packets are let pass through than what the
 * <i>bottleneck resource capacity</i> allows.</p>
 * 
 * <p>The bottleneck resource that we study using this router is the
 * memory space, which determines the maximum possible queue length
 * (the queuing capacity).
 * If more packets arrive than the queue (buffer) can hold,
 * the excess packets are discarded.</p>
 * 
 * <p>The router needs help from the simulator environment to
 * know about the progression of time. The simulation clock ticking
 * is signaled by invocation of the method {@link #send(NetworkElement, Packet)}.
 * Every time this method is called, the router performs
 * the work allowed within the elapsed time.
 * Currently, this means that the router will hand over the queued
 * packets in {@link #packetBuffer} to their outgoing links ({@link #outputPorts}).</p>
 * 
 * <p>Note that this class defines an inner class for output ports
 * (see {@link Router.OutputPort}).</p>
 * 
 * @author Ivan Marsic
 * @see simulation.Simulator
 */
public class Router extends NetworkElement {
	/**
	 * Router's forwarding table maps the destination node
	 * (found in the Packet header) to the outgoing link.
	 */
	protected HashMap<NetworkElement, Link> forwardingTable =
		new HashMap<NetworkElement, Link>();

	/**
	 * Output ports associated with the router's links.
	 */
	protected HashMap<Link, OutputPort> outputPorts =
			new HashMap<Link, OutputPort>();

	/** The router buffer capacity, in bytes. If more packets
	 * arrive than the currently available buffer space allows for
	 * queuing ({@link #currentBufferOccupancy}),
	 * the excess packets will be discarded.
	 */
	private int bufferCapacity = 0;

	/**
	 * Current occupancy of the router memory is obtained as
	 * a sum of the packet lengths for all packets currently
	 * queued in the router memory {@link #packetBuffer}.
	 */
	private int currentBufferOccupancy = 0;

	/** Router memory for buffered/queued packets.
	 * Buffer capacity is represented by {@link #bufferCapacity}.
	 * Current memory occupancy is represented by {@link #currentBufferOccupancy}.
	 */
	private	ArrayList<Packet> packetBuffer = null;

	/**
	 * Constructor.
	 * 
	 * @param simulator_ the runtime environment
	 * @param name_ the name given to this router
	 * @param bufferSize_ the given buffer size for the router's memory (in bytes).
	 */
	public Router(Simulator simulator_, String name_, int bufferSize_) {
		super(simulator_, name_);
		this.bufferCapacity = bufferSize_;

		// The list will NOT be allowed to grow once
		// the sum of packet lengths reaches "maxBufferSize"
		packetBuffer = new ArrayList<Packet>(bufferSize_);
	}

	/**
	 * Accessor for retrieving the packet buffering capacity
	 * of this router. (Note that we ignore the packet header,
	 * i.e., it does not count towards the router's memory occupancy.)
	 * 
	 * @return this router's memory capacity [in bytes].
	 */
	public int getMaxBufferSize() {
		return bufferCapacity;
	}

	/**
	 * Adds another entry into the router's forwarding table.
	 * 
	 * @param node_ the network node (hash-table key) with which the specified outgoing link is to be associated
	 * @param outgoingLink_ the outgoing link to be associated with the specified network node
	 */
	public void addForwardingTableEntry(NetworkElement node_, Link outgoingLink_) {
		// Create the output port that will be associated with the new outgoing link:
		OutputPort outputPort_ = new OutputPort(outgoingLink_);

		// Calculate the maximum mismatch ratio for the new outgoing link:
		outputPort_.updateMaxMismatchRatios(forwardingTable.values());

		// Add the new output port to the list:
		outputPorts.put(outgoingLink_, outputPort_);

		// Add the new forwarding table entry:
		forwardingTable.put(node_, outgoingLink_);
	}

	/**
 	 * When this method is called, it is a signal to the router
 	 * to transmit packets on their corresponding outgoing links,
 	 * if there are any packets buffered in the router memory.<BR>
 	 * Only the caller knows when sufficient amount of time has elapsed
 	 * and when it should call this method.
 	 * 
 	 * @param mode_ the processing mode, currently not used and ignored
 	 * @see Router.OutputPort#transmitPackets()
 	 * @see simulation.Simulator#runDirectTopologySimulation(java.nio.ByteBuffer, int)
	 */
	@Override
	public void process(int mode_) {
		// Send out the packets in transmission on ALL outgoing links:
		Iterable<OutputPort> outputPorts_ = outputPorts.values();
		Iterator<OutputPort> portItems_ = outputPorts_.iterator();
		while (portItems_.hasNext()) {
			OutputPort outgoingLink_ = portItems_.next();
			outgoingLink_.transmitPackets();
		}

		// Update the last time this method was called, for future reference
		lastTimeProcessCalled = getSimulator().getCurrentTime();
	}

 	/**
 	 * Currently does nothing. The input parameters are simply ignored.<BR>
 	 * Note that this method may need to be implemented
	 * if the router will send route advertisement packets ...</p>
	 * 
	 * @param dummySource_ [ignored]
 	 * @param dummyPacket_ [ignored]
 	 * @see simulation.network.NetworkElement#send(NetworkElement, Packet)
 	 */
	@Override
 	public void send(NetworkElement dummySource_, Packet dummyPacket_) {
		System.out.println("Router.send():  PANIC -- how did we get here ?!?!?");
 	}

	/**
	 * Buffers the incoming packet in the router memory.
	 * If the {@link #bufferCapacity} memory capacity is exceeded,
	 * the input packet will be discarded.
	 * 
	 * @param source_ the immediate source of the arrived packet
	 * @param receivedPacket_ the packet that arrived on an incoming link
	 * 
	 * @see Router.OutputPort#handleIncomingPacket(NetworkElement, Packet)
	 * @see simulation.network.NetworkElement#handle(NetworkElement, simulation.network.Packet)
	 */
	@Override
	public void handle(NetworkElement source_, Packet receivedPacket_) {
		// Look-up the outgoing link for this packet:
		Link outgoingLink_ = forwardingTable.get(receivedPacket_.destinationAddr);

		// Look-up the associated output port:
		OutputPort outputPort_ =  outputPorts.get(outgoingLink_);

		// Move the packet to the associated the output port:
		outputPort_.handleIncomingPacket(source_, receivedPacket_);
	}


	// ----------------------------------------------------------------------
	/**
	 * Inner class for router's output ports.
	 */
	protected class OutputPort {
		/** The outgoing link associated with this output port. */
		Link outgoingLink = null;

		/**
		 * Holds the packet <em>currently</em> in transmission on the
		 * outgoing link.
		 * The packet will be handed over to its outgoing
		 * link when signaled
		 */
		Packet packetInTransmission = null;

		/**
		 * Mismatch ratio of transmission speeds between the input and
		 * output links of this router.
		 * For example, a mismatch ratio of 10 means that the input
		 * link can transmit packets ten time faster than the output link.
		 * Because of this, the packets arriving at the router must
		 * enter the queue and wait for their turn for transmission
		 * on the output link.
		 * The default assumption is no mismatch (value <code>1.0</code>),
		 * meaning that packets simply pass through the router without buffering.
		 */
		double maxMismatchRatio = 1.0;

		/**
		 * Counts how many packets to receive before one can be sent
		 * if the outgoing link is slower than incoming links.<BR>
		 * Different increments may be associated with different incoming links.
		 * @see #handleIncomingPacket(NetworkElement, Packet)
		 */
		double mismatchCount = 0.0;

		/**
		 * Constructor for the inner class.
		 * @param outgoingLink_ the outgoing link with which this output port will be associated
		 */
		OutputPort(Link outgoingLink_) {
			this.outgoingLink = outgoingLink_;
		}

		/**
		 * Handles an incoming packet that is heading out on this
		 * output port.
		 * The port can hold only the packet currently in transmission.
		 * Any other packets heading on this outgoing link must be
		 * queued in the router's memory, if the space permits.
		 * Otherwise, the packet will be dropped. Therefore, this
		 * method implements the <em>drop-tail queue management policy</em>.
		 * 
		 * @param source_ &nbsp;the communication link through which the packet arrived
		 * @param receivedPacket_ &nbsp;the packet that arrived on an incoming link
		 */
		void handleIncomingPacket(NetworkElement source_, Packet receivedPacket_) {
			// Calculate the mismatch ratio:
			double mismatchRatio_ = calculateMismatchRatio((Link) source_);

			// If there is no packet currently in transmission on the outgoing link:
			if (packetInTransmission == null) {
				// If no mismatch, send this packet right away:
				if (mismatchRatio_ <= 1.0) {
					// Hand over the packet to its outgoing link:
					outgoingLink.send(Router.this, receivedPacket_);
				} else { // Else, its transmission has just started, so buffer the subsequent packets
					// Put the packet that just arrived into transmission:
					packetInTransmission = receivedPacket_;
					// Mark one arrival towards the mismatch ratio:
					mismatchCount = maxMismatchRatio - (maxMismatchRatio / mismatchRatio_);
				}
			} else {	// Try to buffer the incoming packet into router's memory:
				// The router can buffer up to "maxBufferSize" packets,
				// so all packets in excess of this value will be
				// discarded.
				if (currentBufferOccupancy + receivedPacket_.length <= bufferCapacity) {
					packetBuffer.add(receivedPacket_);
					currentBufferOccupancy += receivedPacket_.length;
				} else if (	// This reporting is for debugging purposes only:
				    (Simulator.currentReportingLevel & Simulator.REPORTING_ROUTERS) != 0
				) {
					System.out.println("\t  Router DROPS " + receivedPacket_.toString());
				}

				// Check if it's time to send one packet on the outgoing link:
				if (mismatchCount < 1.0) {
					// Send out the packet currently in transmission:
					outgoingLink.send(Router.this, packetInTransmission);

					// Retrieve the first packet from the router's memory that
					// is heading out on this outgoing link:
					Iterator<Packet> packetItems_ = packetBuffer.iterator();
					while (packetItems_.hasNext()) {
						Packet packet_ = packetItems_.next();
						if (outgoingLink.equals(forwardingTable.get(packet_.destinationAddr))) {
							// This now becomes the packet currently in transmission:
							packetInTransmission = packet_;

							packetItems_.remove();
							// Indicate that a memory space has been vacated:
							currentBufferOccupancy -= packet_.length;

							// Break out of the loop -- we needed only the first such packet:
							break;
						}
					}
					// Reset the mismatch ratio for the next buffered packet:
					mismatchCount = maxMismatchRatio;
				}
				// Mark one arrival towards the mismatch ratio:
				mismatchCount = mismatchCount - (maxMismatchRatio / mismatchRatio_);
			}
		}

		/**
		 * Transmits packets on the outgoing link.
		 * Should be called only when the time is right.
		 * 
		 * @see Router#process(int)
		 */
		void transmitPackets() {
			// Check if there is a packet currently in transmission:
			if (packetInTransmission == null) {
				return;		// there is none -- return
			}

			// How much time is available for transmitting packets
			// queued in router's memory for this outgoing link, if any:
			double transmitTimeBudget_ = getSimulator().getCurrentTime() - lastTimeProcessCalled;

			// Send out the packet in transmission on the outgoing link:
			outgoingLink.send(Router.this, packetInTransmission);
			packetInTransmission = null;

			// Check also whether any queued packets that are
			// heading out on this outgoing link can also go now:
			Iterator<Packet> packetItems_ = packetBuffer.iterator();
			while (packetItems_.hasNext() && transmitTimeBudget_ > 0.0) {
				Packet packet_ = packetItems_.next();
				if (outgoingLink.equals(forwardingTable.get(packet_.destinationAddr))) {
					// Hand over the packet to its outgoing link:
					outgoingLink.send(Router.this, packet_);

					packetItems_.remove();
					// Indicate that a memory space has been vacated:
					currentBufferOccupancy -= packet_.length;

					// Update the remaining transmission time budget for this link
					transmitTimeBudget_ -= outgoingLink.getTransmissionTime();
				}	
			}
		}

		/**
		 * Calculates the maximum mismatch ratio for the associated
		 * outgoing link relative to all other (incoming) links.
		 * @param allLinks_ the list of all other incoming links
		 */
		void updateMaxMismatchRatios(Collection<Link> allLinks_) {
			Iterator<Link> linkItems_ = allLinks_.iterator();
			while (linkItems_.hasNext()) {
				Link incomingLink_ = linkItems_.next();

				// In case our outgoing link is already in the collection, skip it:
				if (outgoingLink.equals(incomingLink_)) continue;

				OutputPort itsOutputPort_ = outputPorts.get(incomingLink_);

				// First calculate the mismatch ratio relative to this "incomingLink_":
				double mismatchRatio_ = calculateMismatchRatio(incomingLink_);
				// If the old mismatch ratio is smaller than the new one, replace it:
				if (maxMismatchRatio < mismatchRatio_) {
					maxMismatchRatio = mismatchRatio_;
				}

				// Also check for the "incomingLink_" if its max ratio changed:
				if (itsOutputPort_.maxMismatchRatio < (1.0 / mismatchRatio_)) {
					itsOutputPort_.maxMismatchRatio = 1.0 / mismatchRatio_;
				}
			}
		}

		/**
		 * Helper method to calculate the mismatch ratio of an
		 * incoming and the outgoing link as:<BR>
		 * <pre> outgoingLinkTransmissionTime / incomingLinkTransmissionTime </pre>
		 * @param incomingLink_ incoming link to compare to
		 * @return the calculated mismatch ratio
		 */
		protected double calculateMismatchRatio(Link incomingLink_) {
			double incomingLinkTransmissionTime_ = incomingLink_.getTransmissionTime();
			double outgoingLinkTransmissionTime_ = outgoingLink.getTransmissionTime();
			// assume no mismatch, meaning that packets simply pass through without buffering
			double mismatchRatio_ = 1.0;
			if (
				incomingLinkTransmissionTime_ != 0.0 && outgoingLinkTransmissionTime_ != 0.0
			) {
				mismatchRatio_ =
					outgoingLinkTransmissionTime_ / incomingLinkTransmissionTime_;
			}
			return mismatchRatio_;
		}
	}
}