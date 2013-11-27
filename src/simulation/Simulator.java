/*
 * Created on Sep 10, 2005
 *
 * Rutgers University, Department of Electrical and Computer Engineering
 * Copyright (c) 2005-2013 Rutgers University
 */
package simulation;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import au.com.bytecode.opencsv.CSVWriter;
import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Packet;
import simulation.network.Router;
import simulation.network.topology.CloudTopology;
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

    public static final String STATISTICS_FILENAME = "statistics";
    public static final String STATISTICS_FILE_EXTENSION = ".csv";

	/** Total data length to send (in bytes).
	 * In reality, this data should be read from a file or another input stream. */
	public static final int TOTAL_DATA_LENGTH = 1000000;

    /**
     * The topology for this simulation. See {@link simulation.network.topology} for choices
     */
    private Topology topology;

    /**
     * The type of congestion avoidance algorithm used in the simulation.
     */
    private String congestionAvoidanceAlgorithm;

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
     * @param numClients The number of clients to use in the topology, if applicable
     * @param numRouters The number of intermediate router nodes between the senders and the receivers
	 */
	public Simulator(String tcpVersion_, int bufferSize_, int rcvWindow_, String topology, int numClients, int numRouters) {
		String tcpReceiverVersion_ = "Tahoe";	// irrelevant, since our receiver endpoint sends only ACKs, not data
		System.out.println(
			"================================================================\n" +
			"          Running TCP " + tcpVersion_ + " sender  (and " +
			tcpReceiverVersion_ + " receiver).\n"
		);

        // Keep track of the congestion avoidance algorithm
        this.congestionAvoidanceAlgorithm = tcpVersion_;

        if (topology.equalsIgnoreCase("direct")) {
            this.topology = new DirectTopology(this, tcpVersion_, bufferSize_, rcvWindow_, numRouters);
        } else if (topology.equalsIgnoreCase("cloud")) {
            this.topology = new CloudTopology(this, tcpVersion_, bufferSize_, rcvWindow_, numClients, numRouters);
        }  else { // Use the DirectTopology by default
            this.topology = new DirectTopology(this, tcpVersion_, bufferSize_, rcvWindow_, numRouters);
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
	public void runDirectTopologySimulation(java.nio.ByteBuffer inputBuffer_, int num_iter_) {

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
        Iterator<Router> routerIterator;
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
            ((DirectTopology) topology).getLinkWithName("link0").process(2);
            // Our main goal is that the sending endpoint handles acknowledgments
            // received via the router in the previous transmission round, if any.

            ((DirectTopology) topology).getSenderEndpoint().process(1);

            // Let the first link again move any packets:
            ((DirectTopology) topology).getLinkWithName("link0").process(1);
            // This time our main goal is that link transports any new
            // data packets from sender to the router.

            // Let the router relay any packets:
            for (int i = 0; i < topology.getRouters().size(); i++) {
                if (i > 0) {
                    ((DirectTopology) topology).getLinkWithName("link" + i).process(2);
                }

                topology.getRouters().get(i).process(0);

                if (i > 0 && i < topology.getRouters().size() - 1) {
                    ((DirectTopology) topology).getLinkWithName("link" + i).process(1);
                }
            }
            // As a result, the router may have transmitted some packets
            // to its adjoining links.

            // Let the second link move any packets:
            ((DirectTopology) topology).getLinkWithName("link" + topology.getRouters().size()).process(2);
            // Our main goal is that the receiver processes the received
            // data segments and generates ACKs. The ACKs will be ready
            // for the trip back to the sending endpoint.

            ((DirectTopology) topology).getReceiverEndpoint().process(2);

            // Let the second link move any packets:
            ((DirectTopology) topology).getLinkWithName("link" + topology.getRouters().size()).process(1);
            // Our main goal is to deliver the ACKs from the receiver to the router.

            // Let the router relay any packets:
            for (int i = topology.getRouters().size() - 1; i >= 0; i--) {
                topology.getRouters().get(i).process(0);

                ((DirectTopology) topology).getLinkWithName("link" + i).process(1);
            }
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
        int actualTotalRetransmitted_ = ((DirectTopology) topology).getSenderEndpoint().getSender().getTotalBytesRetransmitted();
        int numTimeouts = ((DirectTopology) topology).getSenderEndpoint().getSender().getTimeoutCounter();

        // Process the statistics
        processStatistics(actualTotalTransmitted_, actualTotalRetransmitted_, num_iter_, numTimeouts);
	} //end the function runDirectTopologySimulation()

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
    public void runCloudTopologySimulation(java.nio.ByteBuffer inputBuffer_, int num_iter_) {

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

        Endpoint client, server;
        Packet tmpPkt_;
        Iterator<Endpoint> clientIterator = ((CloudTopology) topology).getClientEndpoints().iterator();
        Iterator<Endpoint> serverIterator;
        while (clientIterator.hasNext()) {
            client = clientIterator.next();
            tmpPkt_ = new Packet(topology.getEndpointWithName(topology.getEndpointNameMappings().get(client.getName())), inputBuffer_.array());
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
            Iterator<Link> clientLinkIterator = ((CloudTopology) topology).getLinksContainingName("client").iterator();
            while (clientLinkIterator.hasNext()) {
                clientLink = clientLinkIterator.next();
                clientLink.process(2);
            }

            clientIterator = ((CloudTopology) topology).getClientEndpoints().iterator();
            while (clientIterator.hasNext()) {
                client = clientIterator.next();
                client.process(1);
            }

            // Let the first links again move any packets:
            // Let the first links move any packets:
            clientLinkIterator = ((CloudTopology) topology).getLinksContainingName("client").iterator();
            while (clientLinkIterator.hasNext()) {
                clientLink = clientLinkIterator.next();
                clientLink.process(1);
            }

            // This time our main goal is that link transports any new
            // data packets from sender to the router.

            // Let the routers relay any packets:
            for (int i = 0; i < topology.getRouters().size(); i++) {
                if (i > 0) {
                    ((CloudTopology) topology).getLinkWithName("link" + i).process(2);
                }

                topology.getRouters().get(i).process(0);

                if (i > 0 && i < topology.getRouters().size() - 1) {
                    ((CloudTopology) topology).getLinkWithName("link" + i).process(1);
                }
            }
            // As a result, the router may have transmitted some packets
            // to its adjoining links.

            // Let the second link move any packets:
            Link serverLink;
            Iterator<Link> serverLinkIterator = ((CloudTopology) topology).getLinksContainingName("server").iterator();
            while (serverLinkIterator.hasNext()) {
                serverLink = serverLinkIterator.next();
                serverLink.process(2);
            }
            // Our main goal is that the receiver processes the received
            // data segments and generates ACKs. The ACKs will be ready
            // for the trip back to the sending endpoint.
            serverIterator = ((CloudTopology) topology).getServerEndpoints().iterator();
            while (serverIterator.hasNext()) {
                server = serverIterator.next();
                server.process(2);
            }

            // Let the second link move any packets:
            serverLinkIterator = ((CloudTopology) topology).getLinksContainingName("server").iterator();
            while (serverLinkIterator.hasNext()) {
                serverLink = serverLinkIterator.next();
                serverLink.process(1);
            }
            // Our main goal is to deliver the ACKs from the receiver to the router.

            // Let the router relay any packets:
            for (int i = topology.getRouters().size() - 1; i >= 1; i--) {
                topology.getRouters().get(i).process(0);

                ((CloudTopology) topology).getLinkWithName("link" + i).process(1);
            }

            ((CloudTopology) topology).getLinkWithName("clientLink0").process(1);
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
        int actualTotalTransmitted_ = 0;
        int actualTotalRetransmitted_ = 0;
        int numTimeouts = 0;
        clientIterator = ((CloudTopology) topology).getClientEndpoints().iterator();
        while (clientIterator.hasNext()) {
            client = clientIterator.next();
            actualTotalTransmitted_ += client.getSender().getTotalBytesTransmitted();
            actualTotalRetransmitted_ += client.getSender().getTotalBytesRetransmitted();
            numTimeouts += client.getSender().getTimeoutCounter();
        }

        // Process the statistics
        processStatistics(actualTotalTransmitted_, actualTotalRetransmitted_, num_iter_, numTimeouts);
    } //end the function runCloudTopologySimulation()

    /**
     * Processes the statistics for the simulation by printing them to the console
     * and saving them to a CSV file named statistics.csv. If the CSV file does not exist
     * one will be created in the directory where the simulation is running. If the CSV
     * file does exist then the statistics will be appended to the file.
     *
     * @param actualTotalTransmitted_ The number of bytes successfully transmitted
     * @param actualTotalRetransmitted_ The number of bytes retransmitted
     * @param num_iter_ The number of iterations for the simulator
     * @param numTimeouts The number of timeouts encountered in the simulation
     */
    private void processStatistics(int actualTotalTransmitted_, int actualTotalRetransmitted_, int num_iter_, int numTimeouts) {
        // Calculate the statistics
        String numberOfIterations = String.valueOf(num_iter_);
        String numberOfSenders = "1";
        if (this.topology instanceof CloudTopology) {
            numberOfSenders = String.valueOf(topology.getEndpointNameMappings().keySet().size());
        }
        String numberOfRouters = String.valueOf(topology.getRouters().size());
        String throughput = BigDecimal.valueOf(((double)actualTotalTransmitted_ / 1048576) / (double)num_iter_).toPlainString();
        String retransmissionRatio = "0";
        if (actualTotalTransmitted_ != 0) {
            retransmissionRatio = BigDecimal.valueOf(((double)actualTotalRetransmitted_ / (double)actualTotalTransmitted_) * 100).toPlainString();
        }
        String numberOfTimeouts = String.valueOf(numTimeouts);

        // Print them to the console
        System.out.println(
                "Number of Iterations: " + numberOfIterations
        );

        System.out.println(
                "Number of Senders: " + numberOfSenders
        );

        System.out.println(
                "Number of Routers: " + numberOfRouters
        );

        // Report the throughput:
        System.out.println(
                "Throughput (MB/RTTs): " + throughput
        );

        // Report the retransmission ratio:
        System.out.println(
                "Retransmission Ratio (% per MB): " + retransmissionRatio + "%"
        );

        // Report the number of timeouts:
        System.out.println(
                "Timeouts: " + numberOfTimeouts
        );


        // Write the statistics to a CSV file
        CSVWriter writer = null;
        boolean fileExists = true;
        String topologyFilename = "";
        if (topology instanceof CloudTopology) {
            topologyFilename = "Cloud";
        } else if (topology instanceof DirectTopology) {
            topologyFilename = "Direct";
        }
        String fileName = STATISTICS_FILENAME + congestionAvoidanceAlgorithm + topologyFilename +  STATISTICS_FILE_EXTENSION;

        try {
            File statisticsFile = new File(fileName);
            String[] cells =  new String[7];
            if (!(statisticsFile.exists())){ // Add column headers when the writer is available
                fileExists = false;
            }

            writer = new CSVWriter(new FileWriter(statisticsFile, true), CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_ESCAPE_CHARACTER);

            if (!fileExists){ // Add column headers if this is a new file
                cells[0] = "Number of Iterations";
                cells[1] = "Number of Senders";
                cells[2] = "Number of Routers";
                cells[3] = "Congestion Avoidance Algorithm";
                cells[4] = "Throughput (MB/RTTs)";
                cells[5] = "Retransmission Ratio (% per MB)";
                cells[6] = "Timeouts";
                writer.writeNext(cells);
            }

            cells[0] = numberOfIterations;
            cells[1] = numberOfSenders;
            cells[2] = numberOfRouters;
            cells[3] = congestionAvoidanceAlgorithm;
            cells[4] = throughput;
            cells[5] = retransmissionRatio;
            cells[6] = numberOfTimeouts;
            writer.writeNext(cells);
            writer.close();
        } catch (Exception exception) {
            System.out.println("Unable to write statistics to " + fileName);
        }
    }

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
     * The sixth parameter may optionally specify the number of clients in the topology. 1 is the default.
     * The seventh parameter may optionally specify the number of routers in the topology. 1 is the default.
     * Example argv_:
     *              [0]: Tahoe
     *              [1]: 500
     *              [2]: Direct
     *              [3]: 6244
     *              [4]: 65536
     *              [5]: 4
     *              [6]: 2
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
        int numClients_ = 1; // One sender client in the topology by default
        int numRouters_ = 1; // One router in the topology by default

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

        if (argv_.length > 5) {
            try {
                numClients_ = Integer.valueOf(argv_[5]);
            } catch (Exception e) {
                System.err.println(
                        "The third argument must be the number of clients as an Integer."
                );
                System.exit(1);
            }
        }

        if (argv_.length > 6) {
            try {
                numRouters_ = Integer.valueOf(argv_[6]);
            } catch (Exception e) {
                System.err.println(
                        "The third argument must be the number of routers as an Integer."
                );
                System.exit(1);
            }
        }

		// Create the simulator.
		Simulator simulator = new Simulator(
			argv_[0], bufferSize_ /* in number of packets */, rcvWindow_ /* in bytes */,
                argv_[2] /* topology */, numClients_ /* # of clients*/, numRouters_ /* # of routers */
		);

		// Extract the number of iterations (transmission rounds) to run
		// from the command line argument.
		Integer numIter_ = new Integer(argv_[1]);

		// Create the input buffer that will be sent to the receiver.
		// In reality, the data should be read from a file or another input stream.
		java.nio.ByteBuffer inputBuffer_ = ByteBuffer.allocate(TOTAL_DATA_LENGTH);

		// Run the simulator for the given number of transmission rounds.
        if (simulator.topology instanceof DirectTopology) {
		    simulator.runDirectTopologySimulation(inputBuffer_, numIter_.intValue());
        } else if (simulator.topology instanceof CloudTopology) {
            simulator.runCloudTopologySimulation(inputBuffer_, numIter_.intValue());
        } else {
            throw new IllegalStateException("Unknown topology encountered while trying to run the simulation.");
        }
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
