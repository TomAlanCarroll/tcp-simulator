package simulation.network.topology;

import simulation.Simulator;
import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Router;

import java.util.Iterator;
import java.util.Set;

/**
 * Constructs and simulates a cloud network topology composed of n clients, n servers, 1 router, a link between each
 * client and the router, and a link between the router and the servers.
 */
public class CloudTopology extends Topology {
    /**
     * Constructs the cloud network topology with the some {@link simulation.network.Endpoint}s,
     * {@link simulation.network.Router}(s), and {@link simulation.network.Link}s
     * @param simulator The reference to the {@link simulation.Simulator}
     * @param tcpVersion The TCP version of the sending endpoint&mdash;one of: "Tahoe", "Reno", or "NewReno")
     * @param bufferSize The memory size for the {@link Router} to queue incoming packets
     * @param receiverBufferWindow The size of the receive buffer for the {@link simulation.tcp.Receiver}
     * @param numClients The number of clients (and servers) to simulate
     * @param numRouters The number of intermediate router nodes between the senders and the receivers
     */
    public CloudTopology(Simulator simulator, String tcpVersion, int bufferSize, int receiverBufferWindow, int numClients, int numRouters) {
        super();

        try {
            Endpoint clientEndpoint = null, serverEndpoint = null;
            Link clientLink, serverLink;

            for (int i = 0; i < numClients; i++) {
                // Client Endpoint
                clientEndpoint = new Endpoint(
                        simulator, "client" + i,
                        null, /* to be set below */
                        tcpVersion, receiverBufferWindow
                );
                this.getEndpoints().add(clientEndpoint);

                // Server Endpoint
                serverEndpoint = new Endpoint(
                        simulator, "server" + i,
                        clientEndpoint,
                        tcpVersion, receiverBufferWindow
                );
                this.getEndpoints().add(serverEndpoint);

                clientEndpoint.setRemoteTCPendpoint(serverEndpoint);

                // Add the client server mapping
                this.getEndpointNameMappings().put("client" + i, "server" + i);
            }

            Router router;
            Link link;
            for (int i = 0; i < numRouters; i++) {
                router = new Router(simulator, "router" + i, bufferSize);

                if (i == 0) { // Connect to clients
                    for (int j = 0; j < numClients; j++) {
                        // The transmission time and propagation time for this link
                        // are set by default to zero (negligible compared to clock tick).
                        clientLink = new Link(
                                simulator, "clientLink" + j, getEndpointWithName("client" + j), router,
                                0.001, /* transmission time as fraction of a clock tick */
                                0.001  /* propagation time as fraction of a clock tick */
                        );

                        // Configure the endpoints with their adjoining links:
                        getEndpointWithName("client" + j).setLink(clientLink);

                        // Configure the router's forwarding table:
                        router.addForwardingTableEntry(getEndpointWithName("client" + j), clientLink);

                        this.getLinks().add(clientLink);
                    }
                }

                if (i == numRouters  - 1) { // Connect to receiver
                    for (int j = 0; j < numClients; j++) {
                        // The transmission time and propagation time for this link
                        // are set by default to zero (negligible compared to clock tick).
                        serverLink = new Link(	// all that matters is that t_x(serverLink) = 10 * t_x(clientLink)
                                simulator, "serverLink" + i, getEndpointWithName("server" + j), router,
                                0.01, /* transmission time as fraction of a clock tick */
                                0.001 /* propagation time as fraction of a clock tick */
                        );

                        // Configure the endpoints with their adjoining links:
                        getEndpointWithName("server" + j).setLink(serverLink);

                        // Configure the router's forwarding table:
                        router.addForwardingTableEntry(getEndpointWithName("server" + j), serverLink);

                        this.getLinks().add(serverLink);
                    }
                }

                if (i > 0) {
                    // Connect router to the previous router
                    link = new Link(
                            simulator, "link" + i, getRouterWithName("router" + (i - 1)), router,
                            0.001, /* transmission time as fraction of a clock tick */
                            0.001  /* propagation time as fraction of a clock tick */
                    );

                    // Configure the router's forwarding table:
                    for (int j = 0; j < numClients; j++) {
                        router.addForwardingTableEntry(getEndpointWithName("client" + j), link);
                        getRouterWithName("router" + (i - 1)).addForwardingTableEntry(getEndpointWithName("server" + j), link);
                    }

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
     * Gets the client endpoint(s) in this topology
     * @return The set of client endpoint(s)
     */
    public Set<Endpoint> getClientEndpoints() {
        return getEndpointsContainingName("client");
    }

    /**
     * Gets the server endpoint(s) in this topology
     * @return The set of server endpoint(s)
     */
    public Set<Endpoint> getServerEndpoints() {
        return getEndpointsContainingName("server");
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
