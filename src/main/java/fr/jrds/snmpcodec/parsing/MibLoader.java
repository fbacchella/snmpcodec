package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.MibException.NonCheckedMibException;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Oid;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.TextualConvention;
import fr.jrds.snmpcodec.smi.Trap;


public class MibLoader {

    public static LogAdapter MIBPARSINGLOGGER = LogAdapter.getLogger(MibStore.class.getPackage().getName() + ".MibParsingError");

    private final ModuleListener modulelistener;
    private final ANTLRErrorListener errorListener;
    private final Properties encodings;

    private Set<Oid> allOids = new HashSet<>();
    private final Set<Symbol> badsymbols = new HashSet<>();
    private final Map<Symbol, Oid> buildOids = new HashMap<>();
    private final Map<Oid, OidTreeNode> nodes = new HashMap<>();
    private final Map<Symbol, Syntax> types = new HashMap<>();
    private final Map<Object, Map<Integer, Map<String, Object>>> buildTraps = new HashMap<>();
    private final Map<Symbol, Map<String, Object>> textualConventions = new HashMap<>();
    private final Map<Oid, ObjectTypeBuilder> buildObjects = new HashMap<>();
    private final OidTreeNodeImpl top = new OidTreeNodeImpl();
    private final Map<String, List<OidTreeNode>> names = new HashMap<>();
    private final Set<String> modules = new HashSet<>();

    private final Map<String, Syntax> _syntaxes = new HashMap<>();
    private final Map<OidTreeNode, ObjectType> _objects = new HashMap<>();
    private final Map<OidTreeNode, Map<Integer,Trap>> resolvedTraps = new HashMap<>();
    private MibStore newStore = null;

    public MibLoader() {
        try {
            addRoot("ccitt", 0);
            addRoot("iso", 1);
            addRoot("joint-iso-ccitt", 2);
            addRoot("broken-module", -1);
        } catch (MibException e1) {
        }

        modulelistener = new ModuleListener(this);
        errorListener = new ModuleErrorListener(modulelistener);

        encodings = new Properties();
        try {
            Collections.list(ClassLoader.getSystemResources("modulesencoding.txt")).forEach( i-> {
                try {
                    InputStream is = i.openStream();
                    encodings.load(is);
                } catch (IOException e) {
                    throw new UncheckedIOException("Invalid modules encoding property file: " + e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid modules encoding property file: " + e.getMessage(), e);
        }
    }

    private void addRoot(String name, int num) throws MibException {
        Symbol s = new Symbol(name);
        OidPath path = new OidPath();
        path.add(new OidPath.OidComponent(name, num));
        addOid(s, path, false);
    }

    private void load(Stream<ANTLRInputStream> source) {
        source
        .filter( i -> {modulelistener.firstError = true; return true;} )
        .map(i -> {
            ASNLexer lexer = new ASNLexer(i);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);
            return lexer;
        })
        .map(i -> new CommonTokenStream(i))
        .map(i -> {
            ASNParser parser = new ASNParser(i);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            modulelistener.parser = parser;
            return parser;
        })
        .map(i -> {
            try {
                return i.fileContent();
            } catch (WrappedException e) {
                MIBPARSINGLOGGER.error("Not a valid module: " + e.getMessage() + " "+ e.getLocation());
                return null;
            }
        })
        .filter(i -> i != null)
        .forEach(i -> {
            try {
                ParseTreeWalker.DEFAULT.walk(modulelistener, i);
            } catch (NonCheckedMibException e) {
                try {
                    throw e.getWrapper();
                } catch (MibException.DuplicatedModuleException e2) {
                } catch (MibException e2) {
                    MIBPARSINGLOGGER.error(e2, e2.getMessage());
                }
            }
        });

    }

    public void load(InputStream... sources) throws IOException {
        Stream<ANTLRInputStream> antltrstream = Arrays.stream(sources)
                .map(i -> i.toString())
                .map(i -> {
                    return new ANTLRInputStream(i);
                });
        load(antltrstream);
    }

    public void load(Reader... sources) throws IOException {
        Stream<ANTLRInputStream> antltrstream = Arrays.stream(sources)
                .map(i -> i.toString())
                .map(i -> {
                    return new ANTLRInputStream(i);
                });
        load(antltrstream);
    }

    public void load(String encoding, Path... sources) {
        Stream<ANTLRInputStream> antltrstream = Arrays.stream(sources)
                .map(i -> {
                    try {
                        String moduleencoding = encoding;
                        if (moduleencoding == null) {
                            String filename = i.getFileName().toString();
                            moduleencoding = encodings.getProperty(filename, "ASCII");
                        }
                        if ("skip".equals(moduleencoding)) {
                            return null;
                        }
                        return new ANTLRFileStream(i.toString(), moduleencoding);
                    } catch (IOException e) {
                        MIBPARSINGLOGGER.error("Invalid MIB source %s: %s", i, e.getMessage());
                        return null;
                    }
                })
                .filter( i-> i != null).map(i -> (ANTLRInputStream) i);
        load(antltrstream);
    }

    public void load(Path... sources) {
        load(null, sources);
    }

    public MibStore buildTree() {
        newStore = new MibStoreImpl(top, modules, names, _syntaxes, _objects, resolvedTraps);

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
                MIBPARSINGLOGGER.debug("adding invalid symbol mapping: %s -> %s" , bad, good);
                buildOids.put(bad, buildOids.get(good));
                badsymbols.add(bad);
            }
            if (types.containsKey(good) && ! badsymbols.contains(bad)) {
                MIBPARSINGLOGGER.debug("adding invalid type declaration mapping: %s -> %s" , bad, good);
                types.put(bad, types.get(good));
            }
            if (textualConventions.containsKey(good) && ! badsymbols.contains(bad)) {
                MIBPARSINGLOGGER.debug("adding invalid textual convention declaration mapping: %s -> %s" , bad, good);
                textualConventions.put(bad, textualConventions.get(good));
            }
        });
        sortdOids();
        allOids.forEach(oid -> {
            try {
                int[] content = oid.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                OidTreeNode node = top.add(content, oid.getName(), oid.isTableEntry());
                nodes.put(oid, node);
                names.computeIfAbsent(oid.getName(), i -> new ArrayList<>()).add(node);
            } catch (MibException e) {
                try {
                    int[] content = oid.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                    top.add(content, oid.getName(), oid.isTableEntry());
                } catch (MibException e1) {
                    MIBPARSINGLOGGER.error(e1, e1.getMessage());
                }
                MIBPARSINGLOGGER.error(e, e.getMessage());
            }
        });
        types.entrySet().stream()
        .filter(i -> i.getValue() != null)
        .filter( i-> ! i.getValue().resolve(types))
        .forEach( i-> MIBPARSINGLOGGER.warn("Can't resolve type %s", i.getKey()));
        resolveTextualConventions();
        // Replace some eventually defined TextualConvention with the smarter version
        Symbol dateAndTime = new Symbol("SNMPv2-TC", "DateAndTime");
        if (types.containsKey(dateAndTime)) {
            types.put(dateAndTime, new TextualConvention.DateAndTime());
        }
        Symbol displayString = new Symbol("SNMPv2-TC", "DisplayString");
        if (types.containsKey(displayString)) {
            types.put(displayString, new TextualConvention.DisplayString());
        }
        types.forEach((i,j) -> _syntaxes.put(i.name, j));
        buildObjects.forEach((k,v) -> {
            OidTreeNode node;
            try {
                node = top.find(k.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray());
                ObjectType object = v.resolve(this);
                _objects.put(node, object);
            } catch (MibException e) {
                MIBPARSINGLOGGER.error(e, e.getMessage());
            }
        });
        buildTraps.forEach((i,j) -> {
            try {
                Oid oid;
                if (i instanceof OidPath) {
                    OidPath p = (OidPath) i;
                    oid = new Oid(p.getRoot(), p.getComponents(), null, false);
                } else if (i instanceof Symbol) {
                    Symbol s =  (Symbol) i;
                    oid = buildOids.get(s);
                    if (oid == null) {
                        throw new MibException("Trap's enterprise unknown " + s.toString());
                    }
                } else {
                    throw new MibException("Wrong enterprise type " + i.getClass().getName());
                }
                int[] oidPath;
                List<Integer> path = oid.getPath(buildOids);
                if (path == null) {
                    throw new MibException("Can't resolve path " + oid);
                }
                oidPath = path.stream().mapToInt( k -> k.intValue()).toArray();
                OidTreeNode node = top.find(oidPath);
                Map<Integer, Trap> traps = new HashMap<>(j.size());
                j.forEach((k,l) -> {
                    try {
                        traps.put(k, new Trap(l));
                    } catch (MibException e) {
                        MIBPARSINGLOGGER.warn("Invalid trap: %s", e.getMessage());
                    }
                });
                resolvedTraps.put(node, traps);
            } catch (MibException e1) {
                MIBPARSINGLOGGER.warn("Invalid trap: %s", e1.getMessage());
            }
        });
        return newStore;
    }

    private void resolveTextualConventions() {
        int resolvCount = 0;
        int oldResolvCount = -1;
        Set<Symbol> notDone = new HashSet<>(textualConventions.keySet());
        while (textualConventions.size() != resolvCount && resolvCount != oldResolvCount) {
            oldResolvCount = resolvCount;
            for (Map.Entry<Symbol, Map<String, Object>> e: textualConventions.entrySet()) {
                Symbol s = e.getKey();
                if (! notDone.contains(s)){
                    continue;
                } else {
                    Map<String, Object> attributes = e.getValue();
                    Syntax type = (Syntax) attributes.get("SYNTAX");
                    String hint = (String) attributes.get("DISPLAY-HINT");
                    boolean resolved = type.resolve(types);
                    if (resolved) {
                        notDone.remove(s);
                        resolvCount++;
                        try {
                            TextualConvention tc = type.getTextualConvention(hint, type);
                            types.put(s, tc);
                        } catch (MibException | MibException.NonCheckedMibException ex) {
                            types.put(s, null);
                            MIBPARSINGLOGGER.warn("Invalid textual convention  %s %s", s, ex.getMessage());
                        }
                        if (resolvCount == textualConventions.size()) {
                            break;
                        }
                    }
                }
            }
        }
        if (notDone.size() > 0) {
            MIBPARSINGLOGGER.debug("missing textual convention %d", notDone.size());
        }
    }

    private void sortdOids() {
        Set<Oid> sortedoid = new TreeSet<>(new Comparator<Oid>() {

            @Override
            public int compare(Oid o1, Oid o2) {
                try {
                    int sorted = Integer.compare(o1.getPath(buildOids).size(), o2.getPath(buildOids).size());
                    if (sorted == 0) {
                        List<Integer> l1 = o1.getPath(buildOids);
                        List<Integer> l2 = o2.getPath(buildOids);
                        int length = l1.size();
                        for (int i = 0 ; i < length ; i++) {
                            int v1 = l1.get(i);
                            int v2 = l2.get(i);
                            sorted = Integer.compare(v1, v2);
                            if (sorted != 0) {
                                break;
                            }
                        }
                    }
                    if (sorted == 0) {
                        sorted = String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
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
                MIBPARSINGLOGGER.error("Can't add new OID %s: %s", i, e.getMessage());
                try {
                    if (! i.getPath(buildOids).isEmpty()) {
                        sortedoid.add(i);
                    }
                } catch (MibException e1) {
                    MIBPARSINGLOGGER.error("Second failure: can't add new OID %s: %s", i, e.getMessage());
                }

            }
        });
        allOids = sortedoid;
    }

    void resolve(Syntax syntax) {
        syntax.resolve(types);
    }

    OidTreeNode resolveNode(Symbol s) {
        if (buildOids.containsKey(s)) {
            Oid o = buildOids.get(s);
            return nodes.get(o);
        } else {
            return null;
        }
    }

    /**
     * Prepare a new module
     * @param currentModule
     * @throws MibException 
     */
    void newModule(String currentModule) throws MibException {
        if (modules.contains(currentModule)) {
            throw new MibException.DuplicatedModuleException(currentModule);
        } else {
            modules.add(currentModule);
        }
    }


    void addMacroValue(Symbol s, String name, Map<String, Object> attributes, OidPath value) throws MibException {
        addOid(s, value, false);
    }

    void addTrapType(Symbol s, Object enterprise, Map<String, Object> attributes, Number trapIndex) throws MibException {
        attributes.put("SYMBOL", s);
        Map<Integer, Map<String,Object>> traps = buildTraps.computeIfAbsent(enterprise, k -> new HashMap<>());
        if (traps.containsKey(trapIndex.intValue())) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        traps.put(trapIndex.intValue(), attributes);
    }

    void addObjectType(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        ObjectTypeBuilder newtype = new ObjectTypeBuilder(attributes);
        Oid oid = addOid(s, value, newtype.isIndexed());
        buildObjects.put(oid, newtype);
    }

    public void addTextualConvention(Symbol s, Map<String, Object> attributes) throws MibException {
        if (textualConventions.containsKey(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        textualConventions.put(s, attributes);
    }

    public void addModuleIdentity(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        addOid(s, value, false);
    }

    public void addType(Symbol s, Syntax type) throws MibException {
        if (types.containsKey(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        types.put(s, type);
    }

    public void addValue(Symbol s, Syntax syntax, OidPath value) throws MibException {
        addOid(s, value, false);
    }

    private Oid addOid(Symbol s, OidPath p, boolean tableEntry) throws MibException {
        p.getAll(tableEntry).forEach( i-> {
            if (i.getName() != null &&  ! allOids.contains(i)) {
                allOids.add(i);
            }
        });
        Oid oid = new Oid(p.getRoot(), p.getComponents(), s.name, tableEntry);
        allOids.add(oid);
        if (!buildOids.containsKey(s)) {
            buildOids.put(s, oid);
        } else {
            throw new MibException.DuplicatedSymbolException(s);
        }
        return oid;
    }

}
