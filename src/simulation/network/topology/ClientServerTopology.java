package simulation.network.topology;

import simulation.Simulator;
import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Router;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Constructs and simulates a client-server network topology composed of n clients, 1 server, 1 router, a link between each
 * client and the router, and 1 link between the router and the server.
 */
public class ClientServerTopology extends Topology {
    /**
     * Constructs the client-server network topology with the some {@link simulation.network.Endpoint}s,
     * {@link simulation.network.Router}(s), and {@link simulation.network.Link}s
     * @param simulator The reference to the {@link simulation.Simulator}
     * @param tcpVersion The TCP version of the sending endpoint&mdash;one of: "Tahoe", "Reno", or "NewReno")
     * @param bufferSize The memory size for the {@link Router} to queue incoming packets
     * @param receiverBufferWindow The size of the receive buffer for the {@link simulation.tcp.Receiver}
     * @param numClients The number of clients to simulate
     */
    public ClientServerTopology(Simulator simulator, String tcpVersion, int bufferSize, int receiverBufferWindow, int numClients) {
        super();

        try {
            Endpoint clientEndpoint = null;
            Link clientLink;
            Iterator<Endpoint> clientIterator;

            // We assume that the server endpoint only receives
            // packets sent by the client endpoints.
            Endpoint serverEndpoint = new Endpoint(
                    simulator, "server",
                    null, tcpVersion, receiverBufferWindow
            );
            this.getEndpoints().add(serverEndpoint);

            for (int i = 0; i < numClients; i++) {
                // Client Endpoint
                clientEndpoint = new Endpoint(
                        simulator, "client" + i,
                        serverEndpoint,
                        tcpVersion, receiverBufferWindow
                );
                this.getEndpoints().add(clientEndpoint);
            }

            serverEndpoint.setRemoteTCPendpoint(clientEndpoint);

            Router router = new Router(simulator, "router", bufferSize);

            clientIterator = this.getEndpoints().iterator();
            while (clientIterator.hasNext()) {
                Endpoint client = clientIterator.next();

                // The transmission time and propagation time for this link
                // are set by default to zero (negligible compared to clock tick).
                clientLink = new Link(
                        simulator, "link" + client.getName(), client, router,
                        0.001, /* transmission time as fraction of a clock tick */
                        0.001  /* propagation time as fraction of a clock tick */
                );

                client.setLink(clientLink);
            }

            Link serverLink = new Link(	// all that matters is that t_x(Link2) = 10 * t_x(Link1)
                    simulator, "serverLink", serverEndpoint, router,
                    0.01, /* transmission time as fraction of a clock tick */
                    0.001 /* propagation time as fraction of a clock tick */
            );

            // Configure the endpoints with their adjoining links:
            serverEndpoint.setLink(serverLink);

            // Configure the router's forwarding table:
            clientIterator = this.getEndpoints().iterator();
            while (clientIterator.hasNext()) {
                Endpoint client = clientIterator.next();

                router.addForwardingTableEntry(client, client.getLink());

                this.getLinks().add(client.getLink());
            }
            router.addForwardingTableEntry(serverEndpoint, serverLink);

            this.getLinks().add(serverLink);

            this.getRouters().add(router);
        } catch (Exception ex) {
            System.out.println(ex.toString());
            return;
        }
    }

    /**
     * Gets the only client endpoint(s) in this topology
     * @return The set of client endpoint(s)
     */
    public Set<Endpoint> getClientEndpoints() {
        Set<Endpoint> clientEndpoints = new HashSet<Endpoint>();
        Iterator<Endpoint> endpointIterator = this.getEndpoints().iterator();
        while (endpointIterator.hasNext()) {
            Endpoint currentEndpoint = endpointIterator.next();
            if (currentEndpoint.getName().contains("client")) {
                clientEndpoints.add(currentEndpoint);
            }
        }

        return clientEndpoints;
    }

    /**
     * Gets the only server endpoint in this topology
     * @return The server endpoint
     */
    public Endpoint getServerEndpoint() {
        Iterator<Endpoint> endpointIterator = this.getEndpoints().iterator();
        while (endpointIterator.hasNext()) {
            Endpoint currentEndpoint = endpointIterator.next();
            if (currentEndpoint.getName().equalsIgnoreCase("server")) {
                return currentEndpoint;
            }
        }

        throw new IllegalStateException("Unable to find Server Endpoint");
    }

    /**
     * Gets the only router in this topology
     * @return The router
     */
    public Router getRouter() {
        Iterator<Router> endpointIterator = this.getRouters().iterator();
        while (endpointIterator.hasNext()) {
            Router currentRouter = endpointIterator.next();
            if (currentRouter.getName().equalsIgnoreCase("router")) {
                return currentRouter;
            }
        }

        throw new IllegalStateException("Unable to find Router");
    }

    /**
     * Gets link1 which is the link between the sender and router
     * @return The link between the sender and router
     */
    public Set<Link> getClientLinks() {
        Set<Link> clientLinks = new HashSet<Link>();
        Iterator<Link> linkIterator = this.getLinks().iterator();
        while (linkIterator.hasNext()) {
            Link currentLink = linkIterator.next();
            if (currentLink.getName().contains("client")) {
                clientLinks.add(currentLink);
            }
        }

        return clientLinks;
    }

    /**
     * Gets the link between the server and router
     * @return The link between the server and router
     */
    public Link getServerLink() {
        Iterator<Link> linkIterator = this.getLinks().iterator();
        while (linkIterator.hasNext()) {
            Link currentLink = linkIterator.next();
            if (currentLink.getName().equalsIgnoreCase("serverLink")) {
                return currentLink;
            }
        }

        throw new IllegalStateException("Unable to find server link");
    }
}
