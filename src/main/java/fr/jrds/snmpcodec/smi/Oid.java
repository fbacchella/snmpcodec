package fr.jrds.snmpcodec.smi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.parsing.OidPath.OidComponent;

public class Oid {

    private final Symbol root;
    private final List<OidComponent> components;
    private final boolean tableEntry;
    private List<Integer> path = null;
    private final String name;

    public Oid(Symbol root, List<OidComponent> components, String name, boolean tableEntry) throws MibException {
        if (components.size() == 0 && root == null) {
            throw new MibException("Creating empty OID " + components);
        }
        this.root = root;
        this.components = components;
        this.tableEntry = tableEntry;
        this.name = name;
    }

    public Oid(int[] components, String name) throws MibException {
        if (components.length == 0) {
            throw new MibException("Creating empty OID");
        }
        this.path = Arrays.stream(components).mapToObj(Integer::valueOf).collect(Collectors.toList());
        this.tableEntry = false;
        this.components = null;
        this.root = null;
        this.name = name;
    }

    public int getNativeSize() {
        return components == null ? 0 : components.size();
    }

    public Stream<Oid> getHierarchy(Map<String, Symbol> names) {
        if (components == null || components.size() <= 2) {
            return Stream.empty();
        } else {
            components.stream().forEach( i -> {
                System.out.format("    %s(%s)\n", i.name, i.number);
            });
            return Stream.empty();
        }
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
            components.forEach( i-> {
                path.add(i.number);
            });
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
            return (root != null ? root  + "." : "") + components.stream().map(i -> i.toString()).collect(Collectors.joining("."));
        } else {
            return path.stream().map(i -> i.toString()).collect(Collectors.joining("."));
        }
    }

    /**
     * @return the tableEntry
     */
    public boolean isTableEntry() {
        return tableEntry;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

}
