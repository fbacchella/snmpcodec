package fr.jrds.snmpcodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.objects.DeclaredType;
import fr.jrds.snmpcodec.objects.DisplayHint;
import fr.jrds.snmpcodec.objects.Oid;
import fr.jrds.snmpcodec.objects.Oid.OidComponent;
import fr.jrds.snmpcodec.objects.Oid.OidPath;
import fr.jrds.snmpcodec.objects.Symbol;

public class MibStore {

    LogAdapter logger = LogAdapter.getLogger(MibStore.class);

    private final Set<Symbol> badsymbols = new HashSet<>();
    public final Map<Symbol, DeclaredType<?>> types = new Symbol.SymbolMap<>();
    public final Map<Symbol, Oid> oids = new Symbol.SymbolMap<>();
    public final Map<Symbol, DisplayHint> hints = new Symbol.SymbolMap<>();
    public final Map<Symbol, Map<Integer, Map<String, Object>>> traps = new Symbol.SymbolMap<>();
    public final OidTreeNode top = new OidTreeNode();
    private final Set<String> modules = new HashSet<>();

    public MibStore() {
        Symbol ccitt = new Symbol("CCITT", "ccitt");
        Symbol iso = new Symbol("ISO", "iso");
        oids.put(ccitt, new Oid(new int[]{0}, oids));
        oids.put(iso, new Oid(new int[]{1}, oids));
        try {
            top.add(new int[]{0}, ccitt);
            top.add(new int[]{1}, iso);
        } catch (MibException e) {
        }

        //        // iso oid is not defined in any mibs, and may be called in different modules
        //        OidComponent isocomponent = new OidComponent();
        //        isocomponent.value = 1;
        //        OidPath isopath = OidPath.getRoot(1);
        //        Oid iso = new Oid(isopath, oids);
        //        oids.put(new Symbol("ISO", "iso"), iso);
        //        top.add(new int[]{1}, iso);
        //        oids.put(new Symbol("CCITT", "ccitt"), new Oid(OidPath.getRoot(0), oids));
        DisplayHint.addAnnotation(DisplayHint.DateAndTime.class, hints);
    }

    public void addType(Symbol s, DeclaredType<?> type) {
        if (! types.containsKey(s)) {
            types.put(s, type);
        } else {
            logger.warn("duplicating %s(%s): %s", s, type, types.get(s));
        }
    }

    public void addTextualConvention(Symbol s, Map<String, Object> attributes) {
        DeclaredType<?> type = (DeclaredType<?>) attributes.get("SYNTAX");
        //System.out.format("%s %s\n", s, type.content);
        String hint = (String) attributes.get("DISPLAY-HINT");
        types.put(s, type);
        if (hint != null) {
            DisplayHint.addAnnotation(s, hint, hints);
        }
    }

    public void addValue(Symbol s, DeclaredType<?> type, Object value) {
        if (value instanceof OidPath) {
            addOid(s, (OidPath)value);
        } else {
            //System.out.format("%s %s %s\n", s, type, value.getClass());
        }
    }

    public void addMacroValue(Symbol s, String name, Map<String, Object> attributes, Object value) {
        if (attributes.containsKey("SYNTAX")) {
            addType(s, (DeclaredType<?>)attributes.get("SYNTAX"));
        }
        switch (name) {
        case "TRAP-TYPE":
            Symbol enterprise = (Symbol) attributes.get("ENTERPRISE");
            Number trapIndex = (Number) value;
            attributes.put("SYMBOL".intern(), s);
            traps.computeIfAbsent(enterprise, k -> new HashMap<>()).put(trapIndex.intValue(), attributes);
        case "OBJECT-TYPE":
        case "NOTIFICATION-TYPE":
        case "OBJECT-IDENTITY":
            break;
        case "MODULE-COMPLIANCE":
        case "NOTIFICATION-GROUP":
        case "OBJECT-GROUP":
        case "MODULE-IDENTITY":
        case "AGENT-CAPABILITIES":
            //System.out.println("dropping " +  s);
            break;
        default:
            //System.out.format("%s %s %s(%s)\n", s, name, attributes, value, value.getClass().getName());
        }
        if (value instanceof OidPath) {
            addOid(s, (OidPath)value);
        }
    }

    private void addOid(Symbol s, OidPath p) {
        // Multiple elements defined in the path, add missing ones
        if (p.size() > 2) {
            Symbol lastSymbol = s;
            for(int i=2 ; i < p.size() ; i++) {
                OidPath newPath = new OidPath(p.subList(0, i));
                Symbol newSymbol = p.get(i - 1).symbol;
                // Anonymous name, build using .<value>
                if (newSymbol == null) {
                    newSymbol = new Symbol(lastSymbol.module, lastSymbol.name + "." + p.get(i - 1).value);
                }
                lastSymbol = newSymbol;
                oids.put(newSymbol, new Oid(newPath, oids));
            }
        }
        oids.put(s, new Oid(p, oids));
        //Check for some know broken symbols
    }

    public void buildTree() {
        // Check in provides symbolsalias.txt for known problems or frequent problems in mibs files
        Properties props = new Properties();
        try {
            Collections.list(ClassLoader.getSystemResources("symbolsalias.txt")).forEach( i-> {
                try {
                    InputStream is = i.openStream();
                    props.load(is);
                } catch (IOException e) {
                    throw new UncheckedIOException("Invalid symbols aliases file: " + e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid symbols aliases file: " + e.getMessage(), e);
        }
        props.entrySet().iterator().forEachRemaining( i -> {
            Symbol bad = new Symbol(i.getKey().toString());
            Symbol good = new Symbol(i.getValue().toString());
            if (oids.containsKey(good)) {
                logger.debug("adding invalid symbol mapping: %s -> %s" , bad, good);
                oids.put(bad, oids.get(good));
                badsymbols.add(bad);
            }
            if (types.containsKey(good)) {
                logger.debug("adding invalid type declaration mapping: %s -> %s" , bad, good);
                types.put(bad, types.get(good));
            }
        });

        for (Symbol s: getSortedOids()) {
            try {
                Oid oid = oids.get(s);
                int[] content = oid.getPath().stream().mapToInt(Integer::intValue).toArray();
                top.add(content, s);
            } catch (MibException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private Collection<Symbol> getSortedOids() {
        Map<Oid, Symbol> sortedoid = new TreeMap<>(new Comparator<Oid>() {

            @Override
            public int compare(Oid o1, Oid o2) {
                try {
                    int sorted = Integer.compare(o1.getPath().size(), o2.getPath().size());
                    if (sorted == 0) {
                        sorted = Integer.compare(o1.hashCode(), o2.hashCode());
                    }
                    return sorted;
                } catch (MibException e) {
                    throw new RuntimeException(e.getMessage());
                }
            }

        });

        oids.entrySet().forEach(i -> {
            try {
                if (! i.getValue().getPath().isEmpty()) {
                    sortedoid.put(i.getValue(), i.getKey());
                }
            } catch (MibException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        return sortedoid.values();
    }

    public Object[] parseIndexOID(int[] value) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean containsKey(Symbol s) {
        // TODO Auto-generated method stub
        return false;
    }

    public int[] getFromName(Symbol s) throws MibException {
        oids.get(s).getPath();
        return null;
    }

    public void addTextualConvention(String name, String hint) {
        DisplayHint.addAnnotation(new Symbol(name), hint, hints);
    }

    public void addTextualConvention(Class<? extends DisplayHint> clazz) {
        DisplayHint.addAnnotation(clazz, hints);
    }

    public String format(OID instanceOID, Variable variable) {
        Symbol s = top.find(instanceOID.getValue()).getSymbol();
        if (s == null) {
            return null;
        }
        else if (traps.containsKey(s)) {
            Map<String, Object> trap = traps.get(s).get(variable.toInt());
            if (trap == null) {
                return null;
            } else {
                trap.get("SYMBOL").toString();
            }
        } else if (types.containsKey(s)) {
            do  {
                if (hints.containsKey(s)){
                    return hints.get(s).format(variable);
                }
                DeclaredType td = types.get(s);
            } while (types.containsKey(s));
        }
        return null;
    }


    public Variable parse(OID classOrInstanceOID, String text) {
        // TODO Auto-generated method stub
        return null;
    }

    public void newModule(String currentModule) throws MibException {
        if (modules.contains(currentModule)) {
            throw new MibException("duplicate module declaration " +  currentModule);
        } else {
            modules.add(currentModule);
        }

    }
}
