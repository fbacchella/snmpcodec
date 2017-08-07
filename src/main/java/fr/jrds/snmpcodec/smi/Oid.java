package fr.jrds.snmpcodec.smi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fr.jrds.snmpcodec.MibException;

public class Oid {

    public static class OidComponent {
        public Integer value = null;
        public Symbol symbol = null;
        @Override
        public String toString() {
            return String.format("%s%s", symbol !=null ? symbol.toString() : "", value != null ? String.format("%d", value): "");
        }
    }

    public static class OidPath extends ArrayList<OidComponent> {
        public OidPath() {
            super();
        }

        public OidPath(List<OidComponent> components) {
            super(components);
        }
        

        @Override
        public String toString() {
            return stream().map( i -> i.toString()).collect(Collectors.joining("."));
        }

        public static OidPath getRoot(int value) {
            OidComponent newroot = new OidComponent();
            newroot.value = value;
            OidPath newRootpath = new OidPath();
            newRootpath.add(newroot);
            newRootpath.trimToSize();
            return newRootpath;
        }
    }

    final Map<Symbol, Oid> oids;
    private List<Integer> path = null;
    private OidPath components;
    private final boolean tableEntry;

    public Oid(OidPath components, Map<Symbol, Oid> oids, boolean tableEntry) throws MibException {
        if (components.size() == 0) {
            throw new MibException("Creating empty OID");
        }
        this.oids = oids;
        this.components = components;
        this.tableEntry = tableEntry;
    }

    public Oid(int[] components, Map<Symbol, Oid> oids) throws MibException {
        if (components.length == 0) {
            throw new MibException("Creating empty OID");
        }
        this.oids = oids;
        this.path = Arrays.stream(components).mapToObj(Integer::valueOf).collect(Collectors.toList());
        this.tableEntry = false;
    }

    public List<Integer> getPath() throws MibException {
        if (path == null) {
            path = new ArrayList<>();
            try {
                components.forEach( i-> {
                    try {
                        if (i.value != null) {
                            path.add(i.value);
                        } else if (i.symbol != null) {
                            Oid parent = oids.get(i.symbol);
                            if (parent != null) {
                                Oid step = oids.get(i.symbol);
                                List<Integer> parentpath = step.getPath();
                                if (! parentpath.isEmpty()) {
                                    path.addAll(oids.get(i.symbol).getPath());
                                } else {
                                    throw new MibException("Invalid OID path for symbol " + i.symbol);
                                }
                            } else {
                                throw new MibException(String.format("missing symbol %s in %s",i.symbol, components));
                            }
                        }
                    } catch (MibException e) {
                        throw e.getNonChecked();
                    }
                });
            } catch (MibException.NonCheckedMibException e) {
                path.clear();
                throw e.getWrapper();
            }
        }
        return path;
    }

    @Override
    public String toString() {
        if (path == null) {
            return "";
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

}
