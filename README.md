tcp-simulator
=============

This is a networking simulator for TCP built on top of Ivan Marsic's TCP Simulator

To run the simulator, execute either of these batch files:
1. runCloudSimulation.bat
2. runDirectSimulation.bat

This batch file compiles (using javac) the source code and runs 100 simulations with varying parameters.
Both batch files run each instance of the simulation with 100 simulator ticks. The batch file prompts the user
to select the congestion avoidance algorithm to use for all senders in all of the simulation instances.
The available congestion avoidance algorithms for use in this simulation are:
1. Tahoe
2. Reno
3. NewReno

For the Cloud Topology simulation, the simulation runs starting with 2 clients and gradually increases to 200 clients.
This is not far off from Apache's default MaxClient setting of 256 clients. Alternatively, the number of routers
between the clients and servers can be increased with each run of the simulation. The first run of the simulation
starts with 1 router and gradually increases to 100 routers between the clients and the servers.

For the Direct Topology simulation,the number of routers between the one sender and one receiver is increased
with each run of the simulation. The first run of the simulation starts with 1 router and gradually increases to
100 routers between the one sender and the one receiver. This is useful for simulating a long chain of routers
between a sender and a receiver.

In addition to the output to the console, a CSV containing the simulation statistics will be saved to the directory
where the batch file was executed with a naming convention of "statistics<CongestionAlgorithm><Direct|Cloud>.csv".
For example, a run of the Cloud Topology using the Tahoe congestion avoidance algorithm will be saved to a file named
"statisticsTahoeCloud.csv". The following fields are reported in the CSV file for each simulation run:
1. Number of Iterations for each simulation run
2. Number of Senders (or Clients)
3. Number of Routers
4. Congestion Avoidance Algorithm (either Tahoe, Reno, or NewReno)
5. Throughput (MB/RTTs)
6. Retransmission Ratio (% per MB)
7. Timeouts
