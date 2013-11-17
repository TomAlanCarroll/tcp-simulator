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
import simulation.network.topology.ClientServerTopology;
import simulation.network.topology.DirectTopology;
import simulation.network.topology.Topology;
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

    private Topology topology;

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
	 * @param tcpVersion_ the TCP version of the sending endpoint&mdash;one of: "Tahoe", "Reno", or "NewReno")
	 * @param bufferSize_ the memory size for the {@link Router} to queue incoming packets
	 * @param rcvWindow_ the size of the receive buffer for the {@link simulation.tcp.Receiver}
     * @param topology The topology to use in this simulator
	 */
	public Simulator(String tcpVersion_, int bufferSize_, int rcvWindow_, String topology) {
		String tcpReceiverVersion_ = "Tahoe";	// irrelevant, since our receiver endpoint sends only ACKs, not data
		System.out.println(
			"================================================================\n" +
			"          Running TCP " + tcpVersion_ + " sender  (and " +
			tcpReceiverVersion_ + " receiver).\n"
		);

        if (topology.equalsIgnoreCase("direct")) {
            this.topology = new DirectTopology(this, tcpVersion_, bufferSize_, rcvWindow_);
        } else if (topology.equalsIgnoreCase("client-server")) {
            this.topology = new ClientServerTopology(this, tcpVersion_, bufferSize_, rcvWindow_, 1);
        }  else { // Use the DirectTopology by default
            this.topology = new DirectTopology(this, tcpVersion_, bufferSize_, rcvWindow_);
        }
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

        /**
         * Direct Topology
         */
        if (topology instanceof DirectTopology) {
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
            Packet tmpPkt_ = new Packet(((DirectTopology) topology).getReceiverEndpoint(), inputBuffer_.array());
            ((DirectTopology) topology).getSenderEndpoint().send(null, tmpPkt_);

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
                ((DirectTopology) topology).getLink1().process(2);
                // Our main goal is that the sending endpoint handles acknowledgments
                // received via the router in the previous transmission round, if any.

                ((DirectTopology) topology).getSenderEndpoint().process(1);

                // Let the first link again move any packets:
                ((DirectTopology) topology).getLink1().process(1);
                // This time our main goal is that link transports any new
                // data packets from sender to the router.

                // Let the router relay any packets:
                ((DirectTopology) topology).getRouter().process(0);
                // As a result, the router may have transmitted some packets
                // to its adjoining links.

                // Let the second link move any packets:
                ((DirectTopology) topology).getLink2().process(2);
                // Our main goal is that the receiver processes the received
                // data segments and generates ACKs. The ACKs will be ready
                // for the trip back to the sending endpoint.

                ((DirectTopology) topology).getReceiverEndpoint().process(2);

                // Let the second link move any packets:
                ((DirectTopology) topology).getLink2().process(1);
                // Our main goal is to deliver the ACKs from the receiver to the router.

                // Let the router relay any packets:
                ((DirectTopology) topology).getRouter().process(0);
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
            int actualTotalTransmitted_ = ((DirectTopology) topology).getSenderEndpoint().getSender().getTotalBytesTransmitted();

            // How many bytes could have been transmitted with the given
            // bottleneck capacity, if there were no losses due to
            // exceeding the bottleneck capacity
            // (Note that we add one MSS for the packet that immediately
            // goes into transmission in the router):
            int potentialTotalTransmitted_ =
                (((DirectTopology) topology).getRouter().getMaxBufferSize() + Sender.MSS) * num_iter_;

            // Report the utilization of the sender:
            float utilization_ =
                (float) actualTotalTransmitted_ / (float) potentialTotalTransmitted_;
            System.out.println(
                "Sender utilization: " + Math.round(utilization_*100.0f) + " %"
            );
        }
        /**
         * Client-Server Topology
         */
        else if (topology instanceof ClientServerTopology) {
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

            Endpoint client;
            Packet tmpPkt_;
            Iterator<Endpoint> clientIterator = ((ClientServerTopology) topology).getClientEndpoints().iterator();
            while (clientIterator.hasNext()) {
                tmpPkt_ = new Packet(((ClientServerTopology) topology).getServerEndpoint(), inputBuffer_.array());
                client = clientIterator.next();
                client.send(null, tmpPkt_);
            }

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
                Link clientLink;
                Iterator<Link> clientLinkIterator = ((ClientServerTopology) topology).getClientLinks().iterator();
                while (clientLinkIterator.hasNext()) {
                    clientLink = clientLinkIterator.next();
                    clientLink.process(2);
                }

                clientIterator = ((ClientServerTopology) topology).getClientEndpoints().iterator();
                while (clientIterator.hasNext()) {
                    client = clientIterator.next();
                    client.process(1);
                }

                // Let the first links again move any packets:
                // Let the first links move any packets:
                clientLinkIterator = ((ClientServerTopology) topology).getClientLinks().iterator();
                while (clientLinkIterator.hasNext()) {
                    clientLink = clientLinkIterator.next();
                    clientLink.process(1);
                }

                // This time our main goal is that link transports any new
                // data packets from sender to the router.

                // Let the router relay any packets:
                ((ClientServerTopology) topology).getRouter().process(0);
                // As a result, the router may have transmitted some packets
                // to its adjoining links.

                // Let the second link move any packets:
                ((ClientServerTopology) topology).getServerLink().process(2);
                // Our main goal is that the receiver processes the received
                // data segments and generates ACKs. The ACKs will be ready
                // for the trip back to the sending endpoint.

                ((ClientServerTopology) topology).getServerEndpoint().process(2);

                // Let the second link move any packets:
                ((ClientServerTopology) topology).getServerLink().process(1);
                // Our main goal is to deliver the ACKs from the receiver to the router.

                // Let the router relay any packets:
                ((ClientServerTopology) topology).getRouter().process(0);
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
            int actualTotalTransmitted_ = ((ClientServerTopology) topology).getServerEndpoint().getSender().getTotalBytesTransmitted();

            // How many bytes could have been transmitted with the given
            // bottleneck capacity, if there were no losses due to
            // exceeding the bottleneck capacity
            // (Note that we add one MSS for the packet that immediately
            // goes into transmission in the router):
            int potentialTotalTransmitted_ =
                    (((ClientServerTopology) topology).getRouter().getMaxBufferSize() + Sender.MSS) * num_iter_;

            // Report the utilization of the sender:
            float utilization_ =
                    (float) actualTotalTransmitted_ / (float) potentialTotalTransmitted_;
            System.out.println(
                    "Sender utilization: " + Math.round(utilization_*100.0f) + " %"
            );
        } else {
            throw new IllegalStateException("Unsupported topology selected");
        }
	} //end the function run()

	/** The main method. Takes the number of iterations as
	 * the input and runs the simulator. To run this program,
	 * two arguments must be entered:
	 * <pre>
	 * TCP-sender-version (one of Tahoe/Reno/NewReno) AND number-of-iterations
	 * </pre>
	 * @param argv_ Input argument(s) should contain the version of the
	 * TCP sender (Tahoe/Reno/NewReno), the number of iterations to run, and the topology to use for the simulation.
     * Optionally, the buffer size and size of the receiving window may be specified
     * as the fourth and fifth arguments, respectively.
     * Example argv_:
     *              [0]: Tahoe
     *              [1]: 500
     *              [2]: Direct
     *              [3]: 6244
     *              [4]: 65536
	 */
	public static void main(String[] argv_) {
		if (argv_.length < 3) {
			System.err.println(
				"Please specify the TCP sender version (Tahoe/Reno/NewReno) and the number of iterations!"
			);
			System.exit(1);
		}

        // Defaults: For router buffer, six plus one packet currently in transmission:
        int bufferSize_ = 6*Sender.MSS + 100;	// plus little more for ACKs
        int rcvWindow_ = 65536;	// default 64KBytes

        // Override the buffer size and receiving window size if provided as arguments
        if (argv_.length > 3) {
            try {
                bufferSize_ = Integer.valueOf(argv_[3]);
            } catch (Exception e) {
                System.err.println(
                        "The third argument must be the router buffer size as an Integer."
                );
                System.exit(1);
            }
        }

        if (argv_.length > 4) {
            try {
                rcvWindow_ = Integer.valueOf(argv_[4]);
            } catch (Exception e) {
                System.err.println(
                        "The third argument must be the size of the receive buffer as an Integer."
                );
                System.exit(1);
            }
        }

		// Create the simulator.
		Simulator simulator = new Simulator(
			argv_[0], bufferSize_ /* in number of packets */, rcvWindow_ /* in bytes */, argv_[2] /* topology */
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
