/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * Copyright (c) 2005-2013 Rutgers University
 */
package simulation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Packet;
import simulation.network.Router;
import simulation.tcp.Sender;

/**
 * <p>The <b>main class</b> of a simple simulator for TCP congestion
 * control.<BR> Check also the
 * <a href="http://www.ece.rutgers.edu/~marsic/books/CN/projects/tcp/" target="_top">design
 * documentation for this simulator</a>.
 * <BR>The simulator provides the system clock ticks for time
 * progression and orchestrates the work of the network nodes.
 * The simulated network consists of the network elements of sender-host,
 * router, and receiver-host, connected in a chain as follows:
 * <pre>
 * <center> SENDER <-> NETWORK/ROUTER <-> RECEIVER </center>
 * </pre>
 * The sender host sends only data segments and the receiver host
 * only replies with acknowledgments.  In other words, for simplicity
 * we assume <i>unidirectional transmission</i>.</p>
 * 
 * <p>By default, the simulator reports the values of the congestion
 * control parameters for every iteration:
 * <ol>
 * <li> Iteration number (starting value 1), which is also the simulation
 * clock, in the units of round-trip times (RTTs)</li>
 * <li> Congestion window size in this iteration</li>
 * <li> Effective window size in this iteration</li>
 * <li> Flight size (the number of unacknowledged bytes) in this iteration</li>
 * <li> Slow start threshold size in this iteration</li>
 * </ol>
 * At the end of the simulation, the <i>utilization of the sender</i>
 * is reported.</p>
 * 
 * <p>You can turn ON or OFF different levels of reporting by modifying
 * the variable {@link #currentReportingLevel}.</p>
 * 
 * <p>Only a few parameters can be controlled in this simulator.
 * Rather than have a flexible, multifunctional network simulator
 * that takes long time to understand and use,
 * this simulator is simple for an undergraduate student
 * to understand and use during a semester-long course.
 * The students can modify it with modest effort and study several
 * interesting simulation scenarios described in the
 * <a href="http://www.ece.rutgers.edu/~marsic/books/CN/projects/tcp/" target="_top">design documentation</a>.</p>
 * 
 * @author Ivan Marsic
 */
public class Simulator {
	/** Simulator's reporting flag: <br>
	 * Reports the activities of the simulator runtime environment. */
	public static final int REPORTING_SIMULATOR = 1 << 1;

	/**Simulator's reporting flag: <br>
	 * Reports the activities of communicaTtion links ({@link simulation.network.Link}). */
	public static final int REPORTING_LINKS = 1 << 2;

	/**Simulator's reporting flag: <br>
	 * Reports the activities of {@link simulation.network.Router}. */
	public static final int REPORTING_ROUTERS = 1 << 3;

	/**Simulator's reporting flag: <br>
	 * Reports the activities of {@link simulation.tcp.Sender} and its {@link simulation.tcp.SenderState}. */
	public static final int REPORTING_SENDERS = 1 << 4;

	/**Simulator's reporting flag: <br>
	 * Reports the activities of {@link simulation.tcp.Receiver}. */
	public static final int REPORTING_RECEIVERS = 1 << 5;

	/** Simulator's reporting flag: <br>
	 * Reports the activities of {@link simulation.tcp.RTOEstimator}. */
	public static final int REPORTING_RTO_ESTIMATE = 1 << 6;

	/** This field specifies the current reporting level(s)
	 * for this simulator.<BR>
	 * The minimum possible reporting is obtained by setting the zero value. */
	public static int currentReportingLevel =
//		0;	/* Reports only the most basic congestion parameters. */
		(REPORTING_SIMULATOR | REPORTING_LINKS | REPORTING_ROUTERS | REPORTING_SENDERS);
//		(REPORTING_SIMULATOR | REPORTING_LINKS | REPORTING_ROUTERS | REPORTING_SENDERS | REPORTING_RECEIVERS);
//		(REPORTING_SIMULATOR | REPORTING_LINKS | REPORTING_ROUTERS | REPORTING_SENDERS | REPORTING_RTO_ESTIMATE);

	/** Total data length to send (in bytes).
	 * In reality, this data should be read from a file or another input stream. */
	public static final int TOTAL_DATA_LENGTH = 1000000;

	/** Two endpoints of the TCP connection that will be simulated. */
	private Endpoint senderEndpt = null;
	private Endpoint receiverEndpt = null;

	/** The router that intermediated between the TCP endpoints. */
	private Router router = null;

	/** The communication link that connects {@link #senderEndpt} to {@link #router}. */
	private Link link1 = null;

	/** The communication link that connects {@link #receiverEndpt} to {@link #router}. */
	private Link link2 = null;

	/** Simulation iterations represent the clock ticks for the simulation.
	 * Each iteration is a transmission round, which is one RTT cycle long.
	 * The initial value by default equals "<tt>1.0</tt>". */
	private double currentTime = 1.0;

	/** An array of timers that the simulator has currently registered,
	 * which are associated with {@link TimedComponent}.<BR>
	 * To deal with concurrent events, it is recommended that
	 * timers are fired (if expired) after the component's
	 * functional operation is called. See the design documentation for more details.
	 * @see TimedComponent */
	private	ArrayList<TimerSimulated> timers = new ArrayList<TimerSimulated>();

	/**
	 * Constructor of  the simple TCP congestion control simulator.
	 * Configures the network model: Sender, Router, and Receiver.
	 * The input arguments are used to set up the router, so that it
	 * represents the bottleneck resource.
	 * 
	 * @param tcpSenderVersion_ the TCP version of the sending endpoint&mdash;one of: "Tahoe", "Reno", or "NewReno")
	 * @param bufferSize_ the memory size for the {@link Router} to queue incoming packets
	 * @param rcvWindow_ the size of the receive buffer for the {@link simulation.tcp.Receiver}
	 */
	public Simulator(String tcpSenderVersion_, int bufferSize_, int rcvWindow_) {
		String tcpReceiverVersion_ = "Tahoe";	// irrelevant, since our receiver endpoint sends only ACKs, not data
		System.out.println(
			"================================================================\n" +
			"          Running TCP " + tcpSenderVersion_ + " sender  (and " +
			tcpReceiverVersion_ + " receiver).\n"
		);
		try {
			senderEndpt = new Endpoint(
				this, "sender",
				null /* the receiver endpoint will be set shortly */,
				tcpSenderVersion_, rcvWindow_
			);
			// We assume that the receiver endpoint only receives
			// packets sent by the sender endpoint.
			// However, a curious reader may quickly change this code
			// and have both endpoints send in both directions.
			receiverEndpt = new Endpoint(
				this, "receiver",
				senderEndpt, tcpReceiverVersion_, rcvWindow_
			);
			senderEndpt.setRemoteTCPendpoint(receiverEndpt); // set it now, couldn't set in constructor
		} catch (Exception ex) {
			System.out.println(ex.toString());
			return;
		}
		router = new Router(this, "router", bufferSize_);

		// The transmission time and propagation time for this link
		// are set by default to zero (negligible compared to clock tick).
		link1 = new Link(
			this, "link1", senderEndpt, router,
			0.001, /* transmission time as fraction of a clock tick */
			0.001  /* propagation time as fraction of a clock tick */
		);
		link2 = new Link(	// all that matters is that t_x(Link2) = 10 * t_x(Link1)
			this, "link2", receiverEndpt, router,
			0.01, /* transmission time as fraction of a clock tick */
			0.001 /* propagation time as fraction of a clock tick */
		);

		// Configure the endpoints with their adjoining links:
		senderEndpt.setLink(link1);
		receiverEndpt.setLink(link2);

		// Configure the router's forwarding table:
		router.addForwardingTableEntry(senderEndpt, link1);
		router.addForwardingTableEntry(receiverEndpt, link2);
	}

	/**
	 * Runs the simulator for the given number of transmission rounds
	 * (iterations), starting with the current iteration stored in
	 * the parameter {@link #currentTime}.<BR>
	 * Reports the outcomes of the individual transmissions.
	 * At the end, reports the overall sender utilization.</p>
	 * 
	 * <p><b>Note:</b> The router is invoked to relay only the packets
	 * (and it may drop some of them).  For the sake
	 * of simplicity, the acknowledgment segments simply
	 * bypass the router, so they are never dropped.
	 * 
	 * @param inputBuffer_ the input bytestream to be transported to the receiving endpoint
	 * @param num_iter_ the number of iterations (transmission rounds) to run the simulator
	 */
	public void run(java.nio.ByteBuffer inputBuffer_, int num_iter_) {

		// Print the headline for the output columns.
		// Note that the "time" is given as the integer number of RTTs
		// and represents the current iteration through the main loop.
		System.out.println(
			"Time\tCongWindow\tEffctWindow\tFlightSize\tSSThresh"
		);
		System.out.println(
			"================================================================"
		);

		// The Simulator also plays the role of an Application
		// that is using services of the TCP protocol.
		// Here we provide the input data stream only in the first iteration
		// and in the remaining iterations the system clocks itself
		// -- based on the received ACKs, the sender will keep
		// sending any remaining data.
		//
		// The sender will not transmit the entire input stream at once.
		// Rather, it sends burst-by-burst of segments, as allowed by
		// its congestion window and other parameters,
		// which are set based on the received ACKs.
		Packet tmpPkt_ = new Packet(receiverEndpt, inputBuffer_.array());
		senderEndpt.send(null, tmpPkt_);

		// Iterate for the given number of transmission rounds.
		// Note that an iteration represents a clock tick for the simulation.
		// Each iteration is a transmission round, which is one RTT cycle long.
		for (int iter_ = 0; iter_ <= num_iter_; iter_++) {
			if (
				(Simulator.currentReportingLevel  & Simulator.REPORTING_SIMULATOR) != 0
			) {
				System.out.println(	//TODO prints incorrectly for the first iteration!
					"Start of RTT #" + (int)currentTime +
					" ................................................"
				);
			} else {
				System.out.print(currentTime + "\t");
			}

			// Let the first link move any packets:
			link1.process(2);
			// Our main goal is that the sending endpoint handles acknowledgments
			// received via the router in the previous transmission round, if any.

			senderEndpt.process(1);

			// Let the first link again move any packets:
			link1.process(1);
			// This time our main goal is that link transports any new
			// data packets from sender to the router.

			// Let the router relay any packets:
			router.process(0);
			// As a result, the router may have transmitted some packets
			// to its adjoining links.

			// Let the second link move any packets:
			link2.process(2);
			// Our main goal is that the receiver processes the received
			// data segments and generates ACKs. The ACKs will be ready
			// for the trip back to the sending endpoint.

			receiverEndpt.process(2);

			// Let the second link move any packets:
			link2.process(1);
			// Our main goal is to deliver the ACKs from the receiver to the router.

			// Let the router relay any packets:
			router.process(0);
			// As a result, the router may have transmitted some packets
			// to its adjoining links.

			if (
				(Simulator.currentReportingLevel  & Simulator.REPORTING_SIMULATOR) != 0
			) {
				System.out.println(
					"End of RTT #" + (int)currentTime +
					"   ------------------------------------------------\n"
				);
			}

			// At the end of an iteration, increment the simulation clock by one tick:
			currentTime += 1.0;
		} //end for() loop

		System.out.println(
			"     ====================  E N D   O F   S E S S I O N  ===================="
		);
		// How many bytes were transmitted:
		int actualTotalTransmitted_ = senderEndpt.getSender().getTotalBytesTransmitted();

		// How many bytes could have been transmitted with the given
		// bottleneck capacity, if there were no losses due to
		// exceeding the bottleneck capacity
		// (Note that we add one MSS for the packet that immediately
		// goes into transmission in the router):
		int potentialTotalTransmitted_ =
			(router.getMaxBufferSize() + Sender.MSS) * num_iter_;

		// Report the utilization of the sender:
		float utilization_ =
			(float) actualTotalTransmitted_ / (float) potentialTotalTransmitted_;
		System.out.println(
			"Sender utilization: " + Math.round(utilization_*100.0f) + " %"
		);
	} //end the function run()

	/** The main method. Takes the number of iterations as
	 * the input and runs the simulator. To run this program,
	 * two arguments must be entered:
	 * <pre>
	 * TCP-sender-version (one of Tahoe/Reno/NewReno) AND number-of-iterations
	 * </pre>
	 * @param argv_ Input argument(s) should contain the version of the
	 * TCP sender (Tahoe/Reno/NewReno) and the number of iterations to run.
	 */
	public static void main(String[] argv_) {
		if (argv_.length < 2) {
			System.err.println(
				"Please specify the TCP sender version (Tahoe/Reno/NewReno) and the number of iterations!"
			);
			System.exit(1);
		}

		// Note: You could alter this program, so these values
		// are entered as arguments on the command line, if desired so.

		// For router buffer, six plus one packet currently in transmission:
		int bufferSize_ = 6*Sender.MSS + 100;	// plus little more for ACKs
		int rcvWindow_ = 65536;	// default 64KBytes

		// Create the simulator.
		Simulator simulator = new Simulator(
			argv_[0], bufferSize_ /* in number of packets */, rcvWindow_ /* in bytes */
		);

		// Extract the number of iterations (transmission rounds) to run
		// from the command line argument.
		Integer numIter_ = new Integer(argv_[1]);

		// Create the input buffer that will be sent to the receiver.
		// In reality, the data should be read from a file or another input stream.
		java.nio.ByteBuffer inputBuffer_ = ByteBuffer.allocate(TOTAL_DATA_LENGTH);

		// Run the simulator for the given number of transmission rounds.
		simulator.run(inputBuffer_, numIter_.intValue());
	}

	/**
	 * Returns the current "time" since the start of the simulation.
	 * The time is currently measured in the integer multiples of
	 * the simulation clock "ticks".
	 * 
	 * @return Returns the current iteration of this simulation session.
	 */
	public double getCurrentTime() {
		return currentTime;
	}

	/**
	 * Time increment ("tick") for the simulation clock. Right now
	 * we simply assume that each round takes 1 RTT (and lasts unspecified number of seconds).
	 * @return the time increment (in ticks) for a round of simulation.
	 */
	public double getTimeIncrement() {
		return 1.0;
	}

	/**
	 * Allows a component to start a timer running.
	 * The timer will fire at a specified time.
	 * The timer can be cancelled by calling the
	 * method {@link #cancelTimeout(TimerSimulated)}.</p>
	 * 
	 * <p>Note that the timer object is cloned here,
	 * because the caller may keep reusing the original timer object,
	 * as is done in, e.g., {@link Sender#startRTOtimer()}.<p>
	 * 
	 * @param timer_ a timer to start counting down on the simulated time.
	 * @throws NullPointerException
	 * @throws IllegalArgumentException
	 * @return the handle on the timer, so the caller can cancel this timer if needed
	 */
	public TimerSimulated setTimeoutAt(TimerSimulated timer_)
	throws NullPointerException, IllegalArgumentException {
		TimerSimulated timerCopy_ = (TimerSimulated) timer_.clone();
		if (!timers.add(timerCopy_)) {
			throw new IllegalArgumentException(
				this.getClass().getName() + ".setTimeoutAt():  Attempting to add an existing timer."
			);
		}
		return timerCopy_;
	}

	/**
	 * Allows a component to cancel a running timer.
	 * @param timer_ a running timer to be cancelled.
	 * @throws NullPointerException
	 * @throws IllegalArgumentException
	 */
	public void cancelTimeout(TimerSimulated timer_)
	throws NullPointerException, IllegalArgumentException {
		if (!timers.remove(timer_)) {
			throw new IllegalArgumentException(
				this.getClass().getName() + ".cancelTimeout():  Attempting to cancel a non-existing timer."
			);
		}
	}

	/**
	 * The simulator checks if any running timers
	 * expired because the simulation clock has ticked.
	 * If yes, it fires a timeout event by calling the callback.</p>
	 * 
	 * <p>Note that to deal with with synchronization between concurrent events,
	 * the caller decides which timers will be checked when. It is just to expect
	 * the caller to know such information. In the current implementation,
	 * this method is called from {@link Endpoint#send(simulation.network.NetworkElement, Packet)}
	 * and {@link Endpoint#handle(simulation.network.NetworkElement, Packet)}.</p>
	 * 
	 * @param component_ the timed component for which to check the expired timers
	 */
	public void checkExpiredTimers(TimedComponent component_) {
		// Make a copy of the list for thread-safe traversal
		@SuppressWarnings("unchecked")
		ArrayList<TimerSimulated> timersCopy = (ArrayList<TimerSimulated>) timers.clone();
		ArrayList<TimerSimulated> expiredTimers = new ArrayList<TimerSimulated>();
		// For each timer that is running,
		// if the timer's time arrived (equals the current time),
		// call the callback function of the associated Component.
		Iterator<TimerSimulated> timerItems_ = timersCopy.iterator();
		while (timerItems_.hasNext()) {
			TimerSimulated timer_ = timerItems_.next();
			// If the timeout occurred, call the callback method:
			if (timer_.callback.equals(component_) && timer_.getTime() <= getCurrentTime()) {
				timer_.callback.timerExpired(timer_.type);

				// Mark the timer for removal
				expiredTimers.add(timer_);
			}
		}
		// Release the expired timers since they have accomplished their mission.
		timers.removeAll(expiredTimers);
	}
}
