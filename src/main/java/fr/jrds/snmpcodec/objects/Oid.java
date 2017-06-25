package fr.jrds.snmpcodec.objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.objects.Oid.OidComponent;
import fr.jrds.snmpcodec.objects.Oid.OidPath;

public class Oid {
    
    public static class OidComponent {
        public Integer value = null;
        public Symbol symbol = null;
        @Override
        public String toString() {
            return String.format("%s%s", symbol !=null ? symbol.toString() : "", value != null ? String.format("(%d)", value): "");
        }
    }
    
    public static class OidPath extends ArrayList<OidComponent> {
        public OidPath() {
            super();
        }

        public OidPath(List<OidComponent> components) {
            super(components);
        }

        public static OidPath getRoot(int value) {
            // iso oid is not defined in any mibs, and may be called in different modules
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
    OidPath components;

    public Oid(OidPath components, Map<Symbol, Oid> oids) {
        this.oids = oids;
        this.components = components;
    }

    public Oid(int[] components, Map<Symbol, Oid> oids) {
        this.oids = oids;
        this.path = Arrays.stream(components).mapToObj(Integer::valueOf).collect(Collectors.toList());
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
                                List<Integer> parentpath = oids.get(i.symbol).getPath();
                                if (! parentpath.isEmpty()) {
                                    path.addAll(oids.get(i.symbol).getPath());
                                } else {
                                    throw new RuntimeException("blurb " + components);
                                }
                            } else {
                                throw new RuntimeException(String.format("missing symbol %s in %s\n",i.symbol, components));
                            }
                        }
                    } catch (MibException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                });
            } catch (RuntimeException e) {
                path.clear();
                throw new MibException(e.getMessage());
            }
        }
        return path;
    }
}
