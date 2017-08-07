package fr.jrds.snmpcodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.IndirectSyntax;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Oid;
import fr.jrds.snmpcodec.smi.Oid.OidPath;
import fr.jrds.snmpcodec.smi.Referenced;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.TextualConvention;
import fr.jrds.snmpcodec.smi.ProvidesTextualConvention;

public class MibStore {

    LogAdapter logger = LogAdapter.getLogger(MibStore.class);

    private final Set<Symbol> badsymbols = new HashSet<>();
    public final Map<Symbol, Oid> oids = new Symbol.SymbolMap<>();
    public final Map<String, int[]> names = new HashMap<>();
    public final Map<Symbol, Syntax> codecs = new Symbol.SymbolMap<>();
    public final Map<Symbol, Map<Integer, Map<String, Object>>> traps = new Symbol.SymbolMap<>();
    public final OidTreeNode top = new OidTreeNode();
    private final Set<String> modules = new HashSet<>();
    public Map<Symbol, Map<String, Object>> textualConventions = new HashMap<>();
    public final Map<Symbol, ObjectType> objects = new HashMap<>();
    private final Set<Symbol> symbols = new HashSet<>();

    public MibStore() {
        Symbol ccitt = new Symbol("CCITT", "ccitt");
        Symbol iso = new Symbol("ISO", "iso");
        Symbol joint = new Symbol("JOINT", "joint-iso-ccitt");
        try {
            oids.put(ccitt, new Oid(new int[]{0}, oids));
            oids.put(iso, new Oid(new int[]{1}, oids));
            oids.put(joint, new Oid(new int[]{2}, oids));
            top.add(new int[]{0}, ccitt, false);
            top.add(new int[]{1}, iso, false);
            top.add(new int[]{2}, joint, false);
        } catch (MibException e) {
        }
    }

    public void addValue(Symbol s, Syntax syntax, Object value) throws MibException {
        if (symbols.contains(s)) {
            throw new MibException("Duplicated symbol " +s);
        }
        if (value instanceof OidPath) {
            addOid(s, (OidPath)value, false);
        } else {
            throw new MibException("Unsupported value assignement " + value);
        }
    }

    public void addType(Symbol s, Syntax type) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException("Duplicated symbol " + s);
        }
        codecs.put(s, type);
        symbols.add(s);
    }

    public void addTextualConvention(Symbol s, Map<String, Object> attributes) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException("Duplicated symbol " + s);
        }
        textualConventions.put(s, attributes);
        symbols.add(s);
    }

    public void addObjectType(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException("Duplicated symbol " + s);
        }
        ObjectType newtype = new ObjectType(attributes);
        addOid(s, (OidPath)value, newtype.isIndexed());
        objects.put(s, newtype);
    }

    public void addTrapType(Symbol s, String name, Map<String, Object> attributes, Number trapIndex) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException("Duplicated symbol " + s);
        }
        Symbol enterprise = (Symbol) attributes.get("ENTERPRISE");
        attributes.put("SYMBOL", s);
        traps.computeIfAbsent(enterprise, k -> new HashMap<>()).put(trapIndex.intValue(), attributes);
    }


    public void addModuleIdentity(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException("Duplicated symbol " + s);
        }
        addOid(s, value, false);
    }

    public void addMacroValue(Symbol s, String name, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException("Duplicated symbol " + s);
        }
        addOid(s, value, false);
    }

    private void addOid(Symbol s, OidPath p, boolean tableEntry) throws MibException {
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
                Oid oid = new Oid(newPath, oids, tableEntry);
                if (! oids.containsKey(newSymbol)) {
                    oids.put(newSymbol, oid);
                }
            }
        }
        Oid oid = new Oid(p, oids, tableEntry);
        if (!oids.containsKey(s)) {
            oids.put(s, oid);
        } else {
            logger.debug("Duplicating OID %s -> %s", p, s);
        }
        symbols.add(s);
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
            if (oids.containsKey(good) && ! badsymbols.contains(bad)) {
                logger.debug("adding invalid symbol mapping: %s -> %s" , bad, good);
                oids.put(bad, oids.get(good));
                badsymbols.add(bad);
            }
            if (codecs.containsKey(good) && ! codecs.containsKey(bad)) {
                logger.debug("adding invalid type declaration mapping: %s -> %s" , bad, good);
                codecs.put(bad, codecs.get(good));
            }
        });

        // Replace some eventually defined TextualConvention with the smarter version
        Symbol dateAndTime = new Symbol("SNMPv2-TC", "DateAndTime");
        if (codecs.containsKey(dateAndTime)) {
            codecs.put(dateAndTime, new TextualConvention.DateAndTime());
        }
        Symbol displayString = new Symbol("SNMPv2-TC", "DisplayString");
        if (codecs.containsKey(displayString)) {
            codecs.put(displayString, new TextualConvention.DisplayString());
        }

        for (Symbol s: getSortedOids()) {
            try {
                Oid oid = oids.get(s);
                int[] content = oid.getPath().stream().mapToInt(Integer::intValue).toArray();
                top.add(content, s, oid.isTableEntry());
                names.put(s.name, content);
            } catch (MibException e) {
                System.out.println(e.getMessage());
            }
        }
        resolveTextualConventions();
        symbols.clear();
    }

    private void resolveTextualConventions() {
        textualConventions.forEach((s, attributes) -> {
            Syntax type = (Syntax) attributes.get("SYNTAX");
            String hint = (String) attributes.get("DISPLAY-HINT");
            TextualConvention tc;
            Syntax finaltype = type;
            while (! (finaltype instanceof ProvidesTextualConvention) && finaltype != null) {
                if (finaltype instanceof Referenced) {
                    Referenced ref = (Referenced) finaltype;
                    finaltype = codecs.get(ref.getSym());
                } else if (finaltype instanceof TextualConvention) {
                    TextualConvention temporary = (TextualConvention) finaltype;
                    finaltype = temporary.getSyntax();
                } else {
                    finaltype = ((IndirectSyntax) finaltype).getSyntax();
                }
            }
            if (finaltype != null) {
                try {
                    tc = ((ProvidesTextualConvention)finaltype).getTextualConvention(hint, type);
                    if (tc != null) {
                        codecs.put(s, tc);
                    }
                } catch (Exception e) {
                    System.out.println("Broken hint for textual convention " + s + ": " + hint);
                }
            } else {
                System.out.println("Invalid textual convention " + s + " " + attributes);
            }
        });
        textualConventions = null;
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
                logger.error("Can't add new symbol %s at %s: %s", i.getKey(), i.getValue(), e.getMessage());
            }
        });
        return sortedoid.values();
    }

    public Object[] parseIndexOID(int[] oid) {
        OidTreeNode found = top.search(oid);
        if(found == null) {
            return new Object[] {new OID(oid)};
        }
        List<Object> parts = new ArrayList<Object>();
        int[] foundOID = found.getElements();
        parts.add(found.getSymbol().name);
        //The full path was not found, try to resolve the left other
        if(foundOID.length < oid.length ) {
            Symbol parent = top.find(Arrays.copyOf(foundOID, foundOID.length -1 )).getSymbol();
            if (parent != null) {
                ObjectType parentCodec = objects.get(parent);
                if(parentCodec.isIndexed()) {
                    Index idx = parentCodec.getIndex();
                    int[] index = Arrays.copyOfRange(oid, foundOID.length, oid.length);
                    Arrays.stream(idx.resolve(index, this)).forEach(i -> parts.add(i));
                }
            }
        }
        return parts.toArray(new Object[parts.size()]);
    }

    public boolean containsKey(String text) {
        return names.containsKey(text);
    }

    public int[] getFromName(String text) {
        return names.get(text);
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
        } else if (objects.containsKey(s)) {
            ObjectType ot = objects.get(s);
            return ot.format(variable);
        }
        return null;
    }

    public Variable parse(OID instanceOID, String text) {
        OidTreeNode node = top.search(instanceOID.getValue());
        Symbol s = node.getSymbol();
        if (s == null) {
            return null;
        } else if (codecs.containsKey(s)) {
            return codecs.get(s).parse(text);
        }
        return null;
    }

    /**
     * Prepare a new module
     * @param currentModule
     * @return true if the module is indeed new.
     */
    public boolean newModule(String currentModule) {
        if (modules.contains(currentModule)) {
            return false;
        } else {
            modules.add(currentModule);
            return true;
        }

    }

}
