package simulation.network.topology;

import simulation.Simulator;
import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Router;

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
     */
    public DirectTopology(Simulator simulator, String tcpVersion, int bufferSize, int receiverBufferWindow) {
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
            Router router = new Router(simulator, "router", bufferSize);

            // The transmission time and propagation time for this link
            // are set by default to zero (negligible compared to clock tick).
            Link link1 = new Link(
                    simulator, "link1", senderEndpoint, router,
                    0.001, /* transmission time as fraction of a clock tick */
                    0.001  /* propagation time as fraction of a clock tick */
            );
            Link link2 = new Link(	// all that matters is that t_x(Link2) = 10 * t_x(Link1)
                    simulator, "link2", receiverEndpoint, router,
                    0.01, /* transmission time as fraction of a clock tick */
                    0.001 /* propagation time as fraction of a clock tick */
            );

            // Configure the endpoints with their adjoining links:
            senderEndpoint.setLink(link1);
            receiverEndpoint.setLink(link2);

            // Configure the router's forwarding table:
            router.addForwardingTableEntry(senderEndpoint, link1);
            router.addForwardingTableEntry(receiverEndpoint, link2);

            this.getLinks().add(link1);
            this.getLinks().add(link2);

            this.getRouters().add(router);
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
     * Gets the only router in this topology
     * @return The router
     */
    public Router getRouter() {
        return getRouterWithName("router");
    }

    /**
     * Gets link1 which is the link between the sender and router
     * @return The link between the sender and router
     */
    public Link getLink1() {
        return getLinkWithName("link1");
    }

    /**
     * Gets link2 which is the link between the receiver and router
     * @return The link between the receiver and router
     */
    public Link getLink2() {
        return getLinkWithName("link2");
    }
}
