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
     */
    public CloudTopology(Simulator simulator, String tcpVersion, int bufferSize, int receiverBufferWindow, int numClients) {
        super();

        try {
            Endpoint clientEndpoint = null, serverEndpoint = null;
            Link clientLink, serverLink;
            Iterator<Endpoint> clientIterator;


            Router router = new Router(simulator, "router", bufferSize);

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

                // The transmission time and propagation time for this link
                // are set by default to zero (negligible compared to clock tick).
                clientLink = new Link(
                        simulator, "clientLink" + i, clientEndpoint, router,
                        0.001, /* transmission time as fraction of a clock tick */
                        0.001  /* propagation time as fraction of a clock tick */
                );
                serverLink = new Link(	// all that matters is that t_x(serverLink) = 10 * t_x(clientLink)
                        simulator, "serverLink" + i, serverEndpoint, router,
                        0.01, /* transmission time as fraction of a clock tick */
                        0.001 /* propagation time as fraction of a clock tick */
                );

                // Configure the endpoints with their adjoining links:
                clientEndpoint.setLink(clientLink);
                serverEndpoint.setLink(serverLink);

                // Configure the router's forwarding table:
                router.addForwardingTableEntry(clientEndpoint, clientLink);
                router.addForwardingTableEntry(serverEndpoint, serverLink);

                this.getLinks().add(clientLink);
                this.getLinks().add(serverLink);

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
     * Gets the only router in this topology
     * @return The router
     */
    public Router getRouter() {
        return getRouterWithName("router");
    }

    /**
     * Gets the links between the clients and the router
     * @return The link between the clients and the router
     */
    public Set<Link> getClientLinks() {
        return getLinksContainingName("client");
    }

    /**
     * Gets the links between the servers and the router
     * @return The link between the servers and the router
     */
    public Set<Link> getServerLinks() {
        return getLinksContainingName("server");
    }
}
