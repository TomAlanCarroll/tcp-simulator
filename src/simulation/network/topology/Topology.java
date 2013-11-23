package simulation.network.topology;

import simulation.network.Endpoint;
import simulation.network.Link;
import simulation.network.Router;

import java.util.*;

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
     * (Optional) The mapping of {@link Endpoint} names from senders to receivers.
     */
    private Map<String, String> endpointNameMappings;

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
        this(new HashSet<Endpoint>(), new HashSet<Link>(), new HashSet<Router>());
    }

    /**
     * Constructor that specifies endpoints, links, and routers
     * @param endpoints The set of endpoints in this topology
     * @param links The set of links between endpoints and routers in this topology
     * @param routers The set of routers in this topology
     */
    public Topology(Set<Endpoint> endpoints, Set<Link> links, Set<Router> routers) {
        this(new HashSet<Endpoint>(), new HashSet<Link>(), new HashSet<Router>(), new HashMap<String, String>());
    }

    /**
     * Constructor that specifies endpoints, links, routers, and endpoint name mappings
     * @param endpoints The set of endpoints in this topology
     * @param links The set of links between endpoints and routers in this topology
     * @param routers The set of routers in this topology
     * @param endpointNameMappings The mapping of {@link Endpoint} names from senders to receivers.
     */
    public Topology(Set<Endpoint> endpoints, Set<Link> links, Set<Router> routers, Map<String, String> endpointNameMappings) {
        this.endpoints = endpoints;
        this.links = links;
        this.routers = routers;
        this.endpointNameMappings = endpointNameMappings;
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
    
    /**
     * Gets the {@link Router} with the specified name
     * @param name The name of the {@link Router} to search for
     * @return The {@link Router} with the specified name
     * @throws IllegalStateException If no {@link Router} exists with that name
     */
    public Router getRouterWithName(String name) {
        Iterator<Router> routerIterator = this.getRouters().iterator();
        while (routerIterator.hasNext()) {
            Router currentRouter = routerIterator.next();
            if (currentRouter.getName().equals(name)) {
                return currentRouter;
            }
        }

        throw new IllegalStateException("Unable to find specified Router with name " + name);
    }

    /**
     * Gets the set of {@link Router}(s) containing the specified name
     * @param name The partial name of the {@link Router}(s) to search for
     * @return The set of {@link Router}(s) containing the specified name
     * @throws IllegalStateException If no {@link Router} exists with that name
     */
    public Set<Router> getRoutersContainingName(String name) {
        Set<Router> routerSet = new HashSet<Router>();
        Iterator<Router> routerIterator = this.getRouters().iterator();
        while (routerIterator.hasNext()) {
            Router currentRouter = routerIterator.next();
            if (currentRouter.getName().contains(name)) {
                routerSet.add(currentRouter);
            }
        }

        return routerSet;
    }

    /**
     * Gets the {@link Link} with the specified name
     * @param name The name of the {@link Link} to search for
     * @return The {@link Link} with the specified name
     * @throws IllegalStateException If no {@link Link} exists with that name
     */
    public Link getLinkWithName(String name) {
        Iterator<Link> linkIterator = this.getLinks().iterator();
        while (linkIterator.hasNext()) {
            Link currentLink = linkIterator.next();
            if (currentLink.getName().equals(name)) {
                return currentLink;
            }
        }

        throw new IllegalStateException("Unable to find specified Link with name " + name);
    }

    /**
     * Gets the set of {@link Link}(s) containing the specified name
     * @param name The partial name of the {@link Link}(s) to search for
     * @return The set of {@link Link}(s) containing the specified name
     * @throws IllegalStateException If no {@link Link} exists with that name
     */
    public Set<Link> getLinksContainingName(String name) {
        Set<Link> linkSet = new HashSet<Link>();
        Iterator<Link> linkIterator = this.getLinks().iterator();
        while (linkIterator.hasNext()) {
            Link currentLink = linkIterator.next();
            if (currentLink.getName().contains(name)) {
                linkSet.add(currentLink);
            }
        }

        return linkSet;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Set<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public Map<String, String> getEndpointNameMappings() {
        return endpointNameMappings;
    }

    public void setEndpointNameMappings(Map<String, String> endpointNameMappings) {
        this.endpointNameMappings = endpointNameMappings;
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
