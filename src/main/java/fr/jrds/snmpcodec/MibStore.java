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
import fr.jrds.snmpcodec.smi.Codec;
import fr.jrds.snmpcodec.smi.DeclaredType;
import fr.jrds.snmpcodec.smi.DeclaredType.Native;
import fr.jrds.snmpcodec.smi.DeclaredType.Referenced;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectTypeMacro;
import fr.jrds.snmpcodec.smi.TextualConvention;
import fr.jrds.snmpcodec.smi.Oid;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Oid.OidPath;
import fr.jrds.snmpcodec.smi.SmiType;

public class MibStore {

    LogAdapter logger = LogAdapter.getLogger(MibStore.class);

    private final Set<Symbol> badsymbols = new HashSet<>();
    public final Map<Symbol, Oid> oids = new Symbol.SymbolMap<>();
    public final Map<String, int[]> names = new HashMap<>();
    public final Map<Symbol, Codec> codecs = new Symbol.SymbolMap<>();
    public final Map<Symbol, DeclaredType<?>> types = new Symbol.SymbolMap<>();
    public final Map<Symbol, Map<Integer, Map<String, Object>>> traps = new Symbol.SymbolMap<>();
    public final OidTreeNode top = new OidTreeNode();
    private final Set<String> modules = new HashSet<>();
    public Map<Symbol, Map<String, Object>> textualConventions = new HashMap<>();

    public MibStore() {
        Symbol ccitt = new Symbol("CCITT", "ccitt");
        Symbol iso = new Symbol("ISO", "iso");
        try {
            oids.put(ccitt, new Oid(new int[]{0}, oids));
            oids.put(iso, new Oid(new int[]{1}, oids));
            top.add(new int[]{0}, ccitt, false);
            top.add(new int[]{1}, iso, false);
        } catch (MibException e) {
        }
        codecs.put(new Symbol("SNMPv2-TC", "DateAndTime"), new TextualConvention.DateAndTime());
        codecs.put(new Symbol("SNMPv2-TC", "DisplayString"), new TextualConvention.DisplayString());
    }

    public void addValue(Symbol s, DeclaredType<?> type, Object value) throws MibException {
        if (value instanceof OidPath) {
            addOid(s, (OidPath)value, false);
        } else {
            throw new MibException("Unsupported value assignement " + value);
        }
    }

    public void addType(Symbol s, DeclaredType<?> type) {
        if (codecs.containsKey(s) ) {
            logger.debug("Duplicating symbol %s", s);
            return;
        }
        types.put(s, type);
        switch (type.getType()) {
        case Native: {
            SmiType st = (SmiType) type.getContent();
            codecs.put(s, st);
            break;
        }
        case Referenced: {
            Symbol ref = (Symbol) type.getContent();
            codecs.put(s, new Codec.Referenced(ref, codecs));
            break;
        }
        case ObjectType: {
            ObjectTypeMacro ot = (ObjectTypeMacro) type.getContent();
            if (! ot.isTable()) {
                codecs.put(s, ot);
            }
            break;
        }
        case Sequenceof: {
            // Ignored type
            break;
        }
        case Sequence: {
            // Ignored type
            break;
        }
        case Choice: {
            // Ignored type
            break;
        }
        default: {
            System.out.format("unmanaged type %s: %s\n", s, type.getType());
            break;
        }
        }
    }

    
    //private static final Symbol Unsigned32 = new Symbol("SNMPv2-SMI","Unsigned32");
    public void addTextualConvention(Symbol s, Map<String, Object> attributes) {
        textualConventions.put(s, attributes);
    }

    private void resolveTextualConventions() {
        textualConventions.forEach((s, attributes) -> {
            DeclaredType<?> type = (DeclaredType<?>) attributes.get("SYNTAX");
            String hint = (String) attributes.get("DISPLAY-HINT");
            TextualConvention tc;
            DeclaredType<?> finaltype = type;
            while (finaltype instanceof Referenced) {
                Referenced ref = (Referenced) type;
                finaltype = this.types.get(ref.content);
            }
            if (hint != null && finaltype instanceof DeclaredType.Native) {
                try {
                    tc = ((DeclaredType.Native)finaltype).content.getTextualConvention(hint, finaltype);
                } catch (Exception e) {
                    System.out.format("Bad hint: %s %s %s %s\n", s, hint, finaltype, finaltype.content);
                    tc = null;
                }
                if("LldpPortNumber".equals(s.name)) {
                    System.out.format("%s %s %s %s\n", s, hint, finaltype, finaltype.content);
                }
            } else if (type instanceof DeclaredType.Native) {
                tc = new TextualConvention.Native((DeclaredType.Native) type);
            } else if (type instanceof DeclaredType.Bits) {
                tc = new TextualConvention.Bits((DeclaredType.Bits) type);
            } else if (type instanceof DeclaredType.Referenced && finaltype != null) {
                tc = new TextualConvention.Native((Native) finaltype);
            } else {
                tc = null;
                System.out.format("%s %s\n", s, attributes);
            }
            if (tc != null) {
                if (codecs.containsKey(s) ) {
                    logger.debug("Duplicating textual convention %s", s);
                    return;
                }
                codecs.put(s, tc);
            }
        });
        textualConventions = null;
    }

    public void addObjectType(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        ObjectTypeMacro newtype = new ObjectTypeMacro(attributes, codecs);
        addType(s, new DeclaredType.ObjectType(newtype));
        addOid(s, (OidPath)value, newtype.isIndex());
    }

    public void addTrapType(Symbol s, String name, Map<String, Object> attributes, Number trapIndex) throws MibException {
        Symbol enterprise = (Symbol) attributes.get("ENTERPRISE");
        attributes.put("SYMBOL", s);
        traps.computeIfAbsent(enterprise, k -> new HashMap<>()).put(trapIndex.intValue(), attributes);
    }

    public void addMacroValue(Symbol s, String name, Map<String, Object> attributes, OidPath value) throws MibException {
        switch (name) {
        case "NOTIFICATION-TYPE":
        case "OBJECT-IDENTITY":
        case "MODULE-COMPLIANCE":
        case "NOTIFICATION-GROUP":
        case "OBJECT-GROUP":
        case "MODULE-IDENTITY":
        case "AGENT-CAPABILITIES":
            //System.out.println("dropping " +  s);
            break;
        default:
            System.out.format("%s %s %s(%s)\n", s, name, attributes, value, value.getClass().getName());
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
                Codec parentCodec = codecs.get(parent);
                if(parentCodec.isIndex()) {
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
        } else if (codecs.containsKey(s)) {
            ObjectTypeMacro ot = (ObjectTypeMacro) codecs.get(s);
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
