package simulation.network.topology;

import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Router;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This class is used for network topologies composed of {@link simulation.network.NetworkElement}s.
 * Each topology is intended to be composed of a set of {@link simulation.network.Endpoint}s,
 * {@link simulation.network.Router}(s), and {@link simulation.network.Link}s
 *
 * @author Tom Carroll
 */
public abstract class Topology {
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

    /**
     * Default constructor
     */
    public Topology () {
        this.endpoints = new HashSet<Endpoint>();
        this.links = new HashSet<Link>();
        this.routers = new HashSet<Router>();
    }

    /**
     * Constructor that specifies endpoints, links, and routers
     * @param endpoints The set of endpoints in this topology
     * @param links The set of links between endpoints and routers in this topology
     * @param routers The set of routers in this topology
     */
    public Topology(Set<Endpoint> endpoints, Set<Link> links, Set<Router> routers) {
        this.endpoints = endpoints;
        this.links = links;
        this.routers = routers;
    }

    /**
     * Gets the {@link Endpoint} with the specified name
     * @param name The name of the {@link Endpoint} to search for
     * @return The {@link Endpoint} with the specified name
     * @throws IllegalStateException If no {@link Endpoint} exists with that name
     */
    public Endpoint getEndpointWithName(String name) {
        Iterator<Endpoint> endpointIterator = this.getEndpoints().iterator();
        while (endpointIterator.hasNext()) {
            Endpoint currentEndpoint = endpointIterator.next();
            if (currentEndpoint.getName().equals(name)) {
                return currentEndpoint;
            }
        }

        throw new IllegalStateException("Unable to find specified Endpoint with name " + name);
    }

    /**
     * Gets the set of {@link Endpoint}(s) containing the specified name
     * @param name The partial name of the {@link Endpoint}(s) to search for
     * @return The set of {@link Endpoint}(s) containing the specified name
     * @throws IllegalStateException If no {@link Endpoint} exists with that name
     */
    public Set<Endpoint> getEndpointsContainingName(String name) {
        Set<Endpoint> endpointSet = new HashSet<Endpoint>();
        Iterator<Endpoint> endpointIterator = this.getEndpoints().iterator();
        while (endpointIterator.hasNext()) {
            Endpoint currentEndpoint = endpointIterator.next();
            if (currentEndpoint.getName().contains(name)) {
                endpointSet.add(currentEndpoint);
            }
        }

        return endpointSet;
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
