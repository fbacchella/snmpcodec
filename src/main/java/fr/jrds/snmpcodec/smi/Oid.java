package fr.jrds.snmpcodec.smi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.parsing.OidPath.OidComponent;

public class Oid {

    private final Symbol root;
    private final List<OidComponent> components;
    private List<Integer> path = null;
    private final String name;
    private final boolean pathfirst;

    public Oid(Symbol root, List<OidComponent> components, String name) throws MibException {
        if (components.isEmpty() && root == null) {
            throw new MibException("Creating empty OID " + components);
        }
        this.root = root;
        this.components = components;
        this.name = name;
        this.pathfirst = false;
    }

    public Oid(List<Integer> path, String name) throws MibException {
        if (path == null || path.isEmpty()) {
            throw new MibException("Creating empty OID " + name);
        }
        this.path = path;
        this.root = null;
        this.components = null;
        this.name = name;
        this.pathfirst = true;
    }

    public int getNativeSize() {
        return components == null ? 0 : components.size();
    }

    public List<Integer> getPath(Map<Symbol, Oid> oids) throws MibException {
        if (path == null) {
            if (root != null) {
                Oid parent = oids.get(root);
                if (parent == null) {
                    failedPath();
                    throw new MibException(String.format("missing root symbol from %s", this));
                }
                List<Integer> parentpath = parent.getPath(oids);
                if (parentpath.isEmpty()) {
                    failedPath();
                    throw new MibException("Invalid OID path for symbol " + root);
                }
                path = new ArrayList<>();
                path.addAll(parentpath);
            }
            // The path was null, not root given
            if (path == null) {
                path = new ArrayList<>();
            }
            components.forEach( i-> path.add(Integer.valueOf(i.number)));
        }
        return path;
    }

    private final void failedPath() {
        path = new ArrayList<>(components.size() + 1);
        path.add(-1);
        components.stream().map( i -> i.number).forEach(i-> path.add(i));
    }

    @Override
    public String toString() {
        if (path == null || path.isEmpty() || path.get(0) == -1) {
            return (root != null ? root  + "." : "") + components.stream().map(OidComponent::toString).collect(Collectors.joining("."));
        } else {
            return path.stream().map(i -> i.toString()).collect(Collectors.joining("."));
        }
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        if (pathfirst) {
            result = prime * result + ((path == null) ? 0 : path.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
        } else {
            result = prime * result + ((root == null) ? 0 : root.hashCode());
            result = prime * result + ((components == null) ? 0 : components.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Oid other = (Oid) obj;
        if (pathfirst) {
            return path.equals(other.path) && name.equals(other.name);
        } else {
            if (root == null) {
                if(other.root != null)
                    return false;
            } else if (!root.equals(other.root))
                return false;
            if(components == null) {
                if(other.components != null)
                    return false;
            } else if(!components.equals(other.components))
                return false;
            if(name == null) {
                if(other.name != null)
                    return false;
            } else if(!name.equals(other.name))
                return false;
            return true;
        }
    }

}
