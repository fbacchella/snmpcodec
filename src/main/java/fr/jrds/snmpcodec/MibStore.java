package fr.jrds.snmpcodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibException.DuplicatedSymbolOid;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.parsing.ObjectTypeBuilder;
import fr.jrds.snmpcodec.parsing.OidPath;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.IndirectSyntax;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Oid;
import fr.jrds.snmpcodec.smi.ProvidesTextualConvention;
import fr.jrds.snmpcodec.smi.Referenced;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.TextualConvention;

public class MibStore {

    LogAdapter logger = LogAdapter.getLogger(MibStore.class);

    private final Set<Symbol> badsymbols = new HashSet<>();
    private Map<Symbol, Oid> buildOids = new HashMap<>();
    private Set<Oid> allOids = new HashSet<>();
    private Map<Symbol, Syntax> buildSyntaxes = new HashMap<>();
    private Map<Symbol, Map<Integer, Map<String, Object>>> buildTraps = new HashMap<>();
    private Map<Symbol, Map<String, Object>> textualConventions = new HashMap<>();
    private Map<Symbol, ObjectTypeBuilder> buildObjects = new HashMap<>();
    private Set<Symbol> symbols = new HashSet<>();
    private Map<Symbol, OidTreeNode> resolvedOids = new HashMap<>();

    public final OidTreeNode top = new OidTreeNode();
    public final Map<String, List<OidTreeNode>> names = new HashMap<>();
    private final Map<OidTreeNode, Syntax> _syntaxes = new HashMap<>();
    public final Map<OidTreeNode, Syntax> syntaxes = Collections.unmodifiableMap(_syntaxes);
    private final Map<OidTreeNode, ObjectType> _objects = new HashMap<>();
    public final Map<OidTreeNode, ObjectType> objects = Collections.unmodifiableMap(_objects);
    private final Map<OidTreeNode, Map<Integer, Map<String, Object>>> _resolvedTraps = new HashMap<>();
    public final Map<OidTreeNode, Map<Integer, Map<String, Object>>> resolvedTraps = Collections.unmodifiableMap(_resolvedTraps);
    private final Set<String> _modules = new HashSet<>();
    public final Set<String> modules = Collections.unmodifiableSet(_modules);


    public MibStore() {
        Symbol ccitt = new Symbol("ccitt");
        Symbol iso = new Symbol("iso");
        Symbol joint = new Symbol("joint-iso-ccitt");
        Symbol broken = new Symbol("broken-module");
        try {
            buildOids.put(ccitt, new Oid(new int[]{0}, ccitt.name));
            buildOids.put(iso, new Oid(new int[]{1}, iso.name));
            buildOids.put(joint, new Oid(new int[]{2}, joint.name));
            buildOids.put(broken, new Oid(new int[]{-1}, broken.name));
            top.add(new int[]{0}, ccitt.name, false);
            top.add(new int[]{1}, iso.name, false);
            top.add(new int[]{2}, joint.name, false);
            top.add(new int[]{-1}, broken.name, false);
        } catch (MibException e) {
        }
    }

    /**
     * Prepare a new module
     * @param currentModule
     * @return true if the module is indeed new.
     * @throws MibException 
     */
    public void newModule(String currentModule) throws MibException {
        if (_modules.contains(currentModule)) {
            throw new MibException.DuplicatedModuleException(currentModule);
        } else {
            _modules.add(currentModule);
        }
    }

    public void addValue(Symbol s, Syntax syntax, Object value) throws MibException {
        if (symbols.contains(s)) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        if (value instanceof OidPath) {
            addOid(s, (OidPath)value, false);
        } else {
            throw new MibException("Unsupported value assignement " + value);
        }
    }

    public void addType(Symbol s, Syntax type) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        buildSyntaxes.put(s, type);
        symbols.add(s);
    }

    public void addTextualConvention(Symbol s, Map<String, Object> attributes) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        textualConventions.put(s, attributes);
        symbols.add(s);
    }

    public void addObjectType(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        ObjectTypeBuilder newtype = new ObjectTypeBuilder(attributes);
        addOid(s, value, newtype.isIndexed());
        buildObjects.put(s, newtype);
    }

    public void addTrapType(Symbol s, String name, Map<String, Object> attributes, Number trapIndex) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        attributes.put("SYMBOL", s);
        Object enterprise = attributes.get("ENTERPRISE");
        if (enterprise instanceof Symbol) {
            buildTraps.computeIfAbsent((Symbol)enterprise, k -> new HashMap<>()).put(trapIndex.intValue(), attributes);
        }
    }

    public void addModuleIdentity(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        addOid(s, value, false);
    }

    public void addMacroValue(Symbol s, String name, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        addOid(s, value, false);
    }

    private void addOid(Symbol s, OidPath p, boolean tableEntry) throws MibException {
        p.getAll(tableEntry).forEach( i-> {
            if (i.getName() != null) {
                allOids.add(i);
            }
        });
        Oid oid = new Oid(p.getRoot(), p.getComponents(), s.name, tableEntry);
        allOids.add(oid);
        if (!buildOids.containsKey(s)) {
            buildOids.put(s, oid);
        } else {
            throw new DuplicatedSymbolOid(oid.toString());
        }
        symbols.add(s);
    }

    public OidTreeNode resolveToBuild(Symbol s) {
        return this.resolvedOids.get(s);
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
            if (buildOids.containsKey(good) && ! badsymbols.contains(bad)) {
                logger.debug("adding invalid symbol mapping: %s -> %s" , bad, good);
                buildOids.put(bad, buildOids.get(good));
                badsymbols.add(bad);
            }
            if (buildSyntaxes.containsKey(good) && ! buildSyntaxes.containsKey(bad)) {
                logger.debug("adding invalid type declaration mapping: %s -> %s" , bad, good);
                buildSyntaxes.put(bad, buildSyntaxes.get(good));
            }
        });
        // Replace some eventually defined TextualConvention with the smarter version
        Symbol dateAndTime = new Symbol("SNMPv2-TC", "DateAndTime");
        if (buildSyntaxes.containsKey(dateAndTime)) {
            buildSyntaxes.put(dateAndTime, new TextualConvention.DateAndTime());
        }
        Symbol displayString = new Symbol("SNMPv2-TC", "DisplayString");
        if (buildSyntaxes.containsKey(displayString)) {
            buildSyntaxes.put(displayString, new TextualConvention.DisplayString());
        }
        sortdOids();
        allOids.forEach(oid -> {
            try {
                int[] content = oid.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                OidTreeNode node = top.add(content, oid.getName(), oid.isTableEntry());
                names.computeIfAbsent(oid.getName(), i -> new ArrayList<>()).add(node);
            } catch (MibException e) {
                try {
                    int[] content = oid.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                    OidTreeNode node = top.add(content, oid.getName(), oid.isTableEntry());
                } catch (MibException e1) {
                    System.out.println(e.getMessage());
                }
                System.out.println(e.getMessage());
            }
        });
        buildObjects.forEach((k,v) -> {
            OidTreeNode node = resolvedOids.get(k);
            _objects.put(node, v.resolve(this, node));
        });
        this.buildSyntaxes.forEach((k, v) -> {
            OidTreeNode node = resolvedOids.get(k);
            _syntaxes.put(node, checkSyntax(node, v));
        });
        resolveTextualConventions();
        symbols.clear();
        buildObjects = null;
        buildOids = null;
        buildSyntaxes = null;
        buildTraps = null;
        resolvedOids = null;
    }

    public Syntax checkSyntax(OidTreeNode node, Syntax syntax) {
        if (syntax instanceof Referenced) {
            Referenced ref = (Referenced) syntax;
            Symbol refSymbol = ref.getSymbol();
            if (refSymbol != null) {
                OidTreeNode refNode = resolvedOids.get(ref.getSymbol());
                ref.resolve(refNode, this);
            }
        }
        return syntax;
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
                    Symbol refSymbol = ref.getSymbol();
                    if (refSymbol != null) {
                        OidTreeNode refNode = resolvedOids.get(ref.getSymbol());
                        ref.resolve(refNode, this);
                        finaltype = buildSyntaxes.get(refSymbol);
                    } else {
                        finaltype = _syntaxes.get(ref.getNode());
                    }
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
                        OidTreeNode node = resolvedOids.get(s);
                        _syntaxes.put(node, tc);
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

    private void sortdOids() {
        Set<Oid> sortedoid = new TreeSet<>(new Comparator<Oid>() {

            @Override
            public int compare(Oid o1, Oid o2) {
                try {
                    int sorted = Integer.compare(o1.getPath(buildOids).size(), o2.getPath(buildOids).size());
                    if (sorted == 0) {
                        sorted = Integer.compare(o1.hashCode(), o2.hashCode());
                    }
                    return sorted;
                } catch (MibException e) {
                    throw e.getNonChecked();
                }
            }

        });

        allOids.forEach( i-> {
            try {
                if (! i.getPath(buildOids).isEmpty()) {
                    sortedoid.add(i);
                }
            } catch (MibException | MibException.NonCheckedMibException e) {
                logger.error("Can't add new OID %s: %s", i, e.getMessage());
                try {
                    if (! i.getPath(buildOids).isEmpty()) {
                        sortedoid.add(i);
                    }
                } catch (MibException e1) {
                    logger.error("Second failure: can't add new OID %s: %s", i, e.getMessage());
                }

            }
        });
        allOids = sortedoid;
    }

    public Object[] parseIndexOID(int[] oid) {
        OidTreeNode found = top.search(oid);
        if(found == null) {
            return new Object[] {new OID(oid)};
        }
        List<Object> parts = new ArrayList<Object>();
        int[] foundOID = found.getElements();
        parts.add(found.getSymbol());
        //The full path was not found, try to resolve the left other
        if(foundOID.length < oid.length ) {
            OidTreeNode parent = top.find(Arrays.copyOf(foundOID, foundOID.length -1 ));
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
        if (names.containsKey(text)) {
            for(OidTreeNode s: names.get(text)) {
                return s.getElements();
            }
        }
        return null;
    }

    public String format(OID instanceOID, Variable variable) {
        OidTreeNode s = top.find(instanceOID.getValue());
        if (s == null) {
            return null;
        }
        else if (buildTraps.containsKey(s)) {
            Map<String, Object> trap = buildTraps.get(s).get(variable.toInt());
            if (trap == null) {
                return null;
            } else {
                trap.get("SYMBOL").toString();
            }
        } else if (buildObjects.containsKey(s)) {
            ObjectType ot = objects.get(s);
            return ot.format(variable);
        }
        return null;
    }

    public Variable parse(OID instanceOID, String text) {
        OidTreeNode node = top.search(instanceOID.getValue());
        if (node == null) {
            return null;
        } else if (syntaxes.containsKey(node)) {
            return syntaxes.get(node).parse(text);
        }
        return null;
    }

}
