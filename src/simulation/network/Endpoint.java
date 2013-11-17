/*
 * Created on Oct 27, 2012
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * <P> Copyright (c) 2005-1012 Rutgers University
 */
package simulation.network;

import simulation.Simulator;
import simulation.tcp.Segment;
import simulation.tcp.SenderNewReno;
import simulation.tcp.Receiver;
import simulation.tcp.SenderReno;
import simulation.tcp.Sender;
import simulation.tcp.SenderTahoe;

/**
 * This class implements a simple TCP endpoint that is composed
 * of sender and receiver objects.
 * The actual work is delegated to the sender when {@link #send(NetworkElement, Packet)}
 * method is called;<BR>
 * to the receiver when {@link #handle(NetworkElement, Packet)} is called
 * with a segment containing non-zero payload;<BR>
 * or to the sender when {@link #handle(NetworkElement, Packet)} is called
 * with a segment having the ACK flag set.
 * 
 * @author Ivan Marsic
 */
public class Endpoint extends NetworkElement {
	/**
	 * Communication link adjoining this endpoint.
	 * This object provides network-layer services, link-layer services, etc.
	 * I'm simply cutting corners because of the lack of time
	 * and I don't want this simulator to become too complex.
	 */
	private Link networkLayerProtocol = null;

	/** Remote endpoint that established a TCP connection
	 * with this local endpoint. */
	protected Endpoint remoteEndpoint = null;

	/** The sender will be created in the constructor based on
	 * the supplied type, such as Tahoe, Reno, etc. */
	protected Sender sender = null;

	/** Created in the constructor; we assume a universal TCP receiver. */
	protected Receiver receiver = null;

	/**
	 * Constructor.
	 * 
	 * @param simulator_ the runtime environment
	 * @param name_ the name given to this endpoint
	 * @param remoteTCPendpoint_ the remote endpoint that established a TCP connection
	 * with this local endpoint, if any
	 * @param senderType_ the TCP version of the Sender contained in this endpoint&mdash;one of: "Tahoe", "Reno", or "NewReno")
	 * @param rcvWindow_ the size of the receive window for the TCP Receiver contained in this endpoint
	 * @throws Exception when an unknown TCP sender type parameter is passed in
	 */
	public Endpoint(
		Simulator simulator_, String name_, Endpoint remoteTCPendpoint_,
		String senderType_, int rcvWindow_
	) throws Exception {
		super(simulator_, name_);
		this.remoteEndpoint = remoteTCPendpoint_;

		if (senderType_.matches("Tahoe")) {
			this.sender = new SenderTahoe(this);
		} else if (senderType_.matches("NewReno")) {
			this.sender = new SenderNewReno(this);
		} else if (senderType_.matches("Reno")) {
			this.sender = new SenderReno(this);
		} else {
			throw new Exception("TCPEndpoint.TCPEndpoint -- unknown TCP sender type.");
		}

		// We assume a universal TCP receiver for all endpoints,
		// regardless of the TCP version of the sender:
		receiver = new Receiver(this, rcvWindow_);
	}

    /**
     * Gets the Link for this Endpoint
     *
     * @return the adjoining communication Link
     */
    public Link getLink() {
        return this.networkLayerProtocol;
    }

	/**
	 * Configures this endpoint with the adjoining
	 * communication link object, the attribute {@link #networkLayerProtocol}.
	 * 
	 * @param adjoiningLink_ the adjoining communication link to set
	 */
	public void setLink(Link adjoiningLink_) {
		this.networkLayerProtocol = adjoiningLink_;
	}

	/**
	 * @return the remote TCP endpoint that is in a TCP session with this endpoint
	 */
	public Endpoint getRemoteTCPendpoint() {
		return remoteEndpoint;
	}

	/**
	 * @param remoteTCPendpoint the remote TCP endpoint to set
	 */
	public void setRemoteTCPendpoint(Endpoint remoteTCPendpoint) {
		this.remoteEndpoint = remoteTCPendpoint;
	}

	/**
	 * @return the local TCP sender component
	 */
	public Sender getSender() {
		return sender;
	}

	/** Returns the receive window size for this endpoint (in bytes)
	 * by getting it from the local Receiver component. */
	public int getLocalRcvWindow() {
		return receiver.getRcvWindow();
	}

	/**
	 * Callback method to call when a simulated timer expires. <BR>
	 * Currently, the Endpoint does not set any timers.
	 * 
	 * @see simulation.TimedComponent
	 */
	public void timerExpired(int timerType_) {
		/* currently does nothing */
	}

	/**
 	 * Before calling the local {@link Sender}, this method
 	 * calls {@link Simulator#checkExpiredTimers(simulation.TimedComponent)}
 	 * to fire the {@link Sender#rtoTimer} if it expired.</p>
 	 * 
 	 * <p>If the received segment contained an ACK, this method will at
 	 * the end call {@link Simulator#checkExpiredTimers(simulation.TimedComponent)}
 	 * to fire the {@link Receiver#delayedACKtimer} and have
 	 * the receiver transmit any cumulative ACKs.</p>
 	 * 
 	 * @param mode_ &nbsp;<em>processing mode</em>: the value <code>1</code>
 	 * requests processing of the sender component of this endpoint; &nbsp;
 	 * the value <code>2</code> requests processing of the receiver component
 	 * of this endpoint
	 */
	@Override
	public void process(int mode_) {
		if (mode_ == 1) {
			// Check if any of the currently running timers expired
			// that are associated with this TCP Sender:
			simulator.checkExpiredTimers(sender);
	
	    	// As a result of received ACKs, the sender's window
	    	// may have opened to send some more segments:
			sender.send(null);
		
		} else if (mode_ == 2) {
			// Check if any of the currently running timers expired
			// that are associated with this TCP Receiver:
			simulator.checkExpiredTimers(receiver);
		}
	}

 	/**
 	 * "Sends" segments by passing them to the network layer protocol object.
 	 * The sending is delegated to the sender object.
 	 * 
 	 * @param source_ the source of the message
 	 * @param newDataPkt_ the new message to send
 	 */
	@Override
 	public void send(NetworkElement source_, Packet newDataPkt_) {
		sender.send(newDataPkt_.dataPayload);
 	}
 
	/**
	 * <p>Handle the data segments received from the remote endpoint.
	 * By default, our in simulator the communication is one-way,
	 * meaning the the sending endpoint sends data and the receiving
	 * endpoint sends only ACKs (no data). However, this implementation
	 * allows for ACKs piggybacked on data segments from the receiving
	 * endpoint. </p>
	 * 
 	 * <p>The actual work is delegated to the sender (for segments
 	 * that carry an acknowledgment) or to the receiver (for
 	 * segments that carry data), or both.</p>
 	 * 
	 * @param packet_ an acknowledgment received from the receiver.
 	 */
	@Override
 	public void handle(NetworkElement source_, Packet packet_) {
 		// Up-cast the input packet to a TCP segment -- what else could it be !?
 		Segment segment_ = (Segment) packet_;
 		if (segment_ == null) {	// not a TCP segment ??
 			if (
 				(Simulator.currentReportingLevel & Simulator.REPORTING_SENDERS) != 0 ||
 				(Simulator.currentReportingLevel & Simulator.REPORTING_RECEIVERS) != 0
 			) {
 				System.out.print("Endpoint.handle(): unknown packet type");
 			}
 			return;
 		}

 		if (segment_.isAck) { // An acknowledgment received from a remote receiver.
 			sender.handle(segment_);
 		}
 
 		if (segment_.length > 0) { // A data segment received from a remote sender.
 			receiver.handle(segment_);
		}
 	}

	/**
	 * @return the network layer protocol for this endpoint
	 */
	public Link getNetworkLayerProtocol() {
		return networkLayerProtocol;
	}
}