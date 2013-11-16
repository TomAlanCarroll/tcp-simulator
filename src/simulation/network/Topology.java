package simulation.network;

import java.util.Set;

/**
 * This class is used for network topologies composed of {@link simulation.network.NetworkElement}s.
 * Each topology is intended to be composed of a set of {@link simulation.network.Endpoint}s,
 * {@link simulation.network.Router}(s), and {@link simulation.network.Link}s
 *
 * @author Tom Carroll
 */
public class Topology {
    /**
     * The set of endpoints in this topology
     */
    private Set<Endpoint> endpoints;

    /**
     * The set of links between endpoints and routers in this topology
     */
    private Set<Link> links;

    /**
     * The set of routers in this topology
     */
    private Set<Router> routers;

    public Topology(Set<Endpoint> endpoints, Set<Link> links, Set<Router> routers) {
        this.endpoints = endpoints;
        this.links = links;
        this.routers = routers;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Set<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public Set<Link> getLinks() {
        return links;
    }

    public void setLinks(Set<Link> links) {
        this.links = links;
    }

    public Set<Router> getRouters() {
        return routers;
    }

    public void setRouters(Set<Router> routers) {
        this.routers = routers;
    }
}
