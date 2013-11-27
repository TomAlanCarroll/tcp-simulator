package simulation.network.topology;

import simulation.Simulator;
import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Router;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Constructs and simulates a simple network topology composed of 2 endpoints, 1 router, and a link between each
 * endpoint and the router.
 */
public class DirectTopology extends Topology {
    /**
     * Constructs the direct topology with the some {@link simulation.network.Endpoint}s,
     * {@link simulation.network.Router}(s), and {@link simulation.network.Link}s
     * @param simulator The reference to the {@link simulation.Simulator}
     * @param tcpVersion The TCP version of the sending endpoint&mdash;one of: "Tahoe", "Reno", or "NewReno")
     * @param bufferSize The memory size for the {@link Router} to queue incoming packets
     * @param receiverBufferWindow The size of the receive buffer for the {@link simulation.tcp.Receiver}
     * @param numRouters The number of intermediate router nodes between the senders and the receivers
     */
    public DirectTopology(Simulator simulator, String tcpVersion, int bufferSize, int receiverBufferWindow, int numRouters) {
        super();

        try {
            // Sender Endpoint
            Endpoint senderEndpoint = new Endpoint(
                    simulator, "sender",
                    null /* the receiver endpoint will be set shortly */,
                    tcpVersion, receiverBufferWindow
            );
            this.getEndpoints().add(senderEndpoint);

            // We assume that the receiver endpoint only receives
            // packets sent by the sender endpoint.
            // However, a curious reader may quickly change this code
            // and have both endpoints send in both directions.
            Endpoint receiverEndpoint = new Endpoint(
                    simulator, "receiver",
                    senderEndpoint, tcpVersion, receiverBufferWindow
            );
            this.getEndpoints().add(receiverEndpoint);

            senderEndpoint.setRemoteTCPendpoint(receiverEndpoint);

            Router router;
            Link link;
            for (int i = 0; i < numRouters; i++) {
                router = new Router(simulator, "router" + i, bufferSize);

                if (i == 0) { // Connect to sender
                    link = new Link(
                            simulator, "link" + i, senderEndpoint, router,
                            0.001, /* transmission time as fraction of a clock tick */
                            0.001  /* propagation time as fraction of a clock tick */
                    );

                    // Configure the endpoints with their adjoining links:
                    senderEndpoint.setLink(link);

                    // Configure the router's forwarding table:
                    router.addForwardingTableEntry(senderEndpoint, link);

                    this.getLinks().add(link);
                }

                if (i == numRouters  - 1) { // Connect to receiver
                    link = new Link(	// all that matters is that t_x(Link2) = 10 * t_x(Link1)
                            simulator, "link" + (i + 1), receiverEndpoint, router,
                            0.01, /* transmission time as fraction of a clock tick */
                            0.001 /* propagation time as fraction of a clock tick */
                    );

                    // Configure the endpoints with their adjoining links:
                    receiverEndpoint.setLink(link);

                    // Configure the router's forwarding table:
                    router.addForwardingTableEntry(receiverEndpoint, link);

                    this.getLinks().add(link);
                }

                if (i > 0) {
                    // Connect router to the previous router
                    link = new Link(
                            simulator, "link" + i, getRouterWithName("router" + (i - 1)), router,
                            0.001, /* transmission time as fraction of a clock tick */
                            0.001  /* propagation time as fraction of a clock tick */
                    );

                    // Configure the router's forwarding table:
                    router.addForwardingTableEntry(senderEndpoint, link);
                    getRouterWithName("router" + (i - 1)).addForwardingTableEntry(receiverEndpoint, link);

                    this.getLinks().add(link);
                }

                this.getRouters().add(router);
            }
        } catch (Exception ex) {
            System.out.println(ex.toString());
            return;
        }
    }

    /**
     * Gets the only sender endpoint in this topology
     * @return The sender endpoint
     */
    public Endpoint getSenderEndpoint() {
        return getEndpointWithName("sender");
    }

    /**
     * Gets the only receiver endpoint in this topology
     * @return The receiver endpoint
     */
    public Endpoint getReceiverEndpoint() {
        return getEndpointWithName("receiver");
    }

    /**
     * Gets Link corresponding to the index
     * @param index The index of the router; Ranges from 0 to n - 1
     * @return The link between the receiver and router
     */
    public Link getLink(int index) {
        return getLinkWithName("link" + index);
    }
}
