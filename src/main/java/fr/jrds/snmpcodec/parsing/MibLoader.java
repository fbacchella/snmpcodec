package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.MibException.NonCheckedMibException;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.AnnotedSyntax;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Oid;
import fr.jrds.snmpcodec.smi.Referenced;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.TextualConvention;
import fr.jrds.snmpcodec.smi.Trap;


public class MibLoader {

    public static final LogAdapter MIBPARSINGLOGGER = LogAdapter.getLogger(MibLoader.class);
    public static final LogAdapter MIBPARSINGLOGGERERROR = LogAdapter.getLogger(MibStore.class.getPackage().getName() + ".MibParsingError");

    private final ModuleListener modulelistener;
    private final ANTLRErrorListener errorListener;
    private final Properties encodings;

    // Those two sets will contains many instance of the same OID
    // The first versions of Oid will depends of the module where it's defined
    // The over will be identified using the exact path to root
    private final Set<Oid> allOids = new HashSet<>();
    private final Set<Oid> tableEntryOid = new HashSet<>();
    private final Set<Symbol> badsymbols = new HashSet<>();
    private final Map<Symbol, Oid> buildOids = new HashMap<>();
    private final Map<List<Integer>, OidTreeNode> nodes = new HashMap<>();
    private final Map<Symbol, Syntax> types = new HashMap<>();
    private final Map<Object, Map<Integer, Map<String, Object>>> buildTraps = new HashMap<>();
    private final Map<Symbol, Map<String, Object>> textualConventions = new HashMap<>();
    private final Map<Oid, ObjectTypeBuilder> buildObjects = new HashMap<>();
    private final OidTreeNodeImpl top = new OidTreeNodeImpl();
    private final Map<String, List<OidTreeNode>> names = new HashMap<>();
    private final Set<String> modules = new HashSet<>();

    private final Map<String, Syntax> syntaxes = new HashMap<>();
    private final Map<OidTreeNode, ObjectType> objects = new HashMap<>();
    private final Map<OidTreeNode, Map<Integer,Trap>> resolvedTraps = new HashMap<>();

    public MibLoader() {
        try {
            addRoot("ccitt", 0);
            addRoot("iso", 1);
            addRoot("joint-iso-ccitt", 2);
            addRoot("broken-module", -1);
        } catch (MibException ex) {
            // Can't be thrown at startup
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

    private void load(Stream<CharStream> source) {
        source
        .filter(i -> {modulelistener.firstError = true; return true;} )
        .map(i -> {
            ASNLexer lexer = new ASNLexer(i);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);
            return lexer;
        })
        .map(CommonTokenStream::new)
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
                MIBPARSINGLOGGERERROR.warn("Not a valid module: " + e.getMessage() + " "+ e.getLocation());
                return null;
            }
        })
        .filter(Objects::nonNull)
        .forEach(i -> {
            try {
                ParseTreeWalker.DEFAULT.walk(modulelistener, i);
            } catch (IllegalStateException e) {
                // The stack was inconsistend during parsing, already handled
            } catch (NonCheckedMibException e) {
                try {
                    throw e.getWrapper();
                } catch (MibException.DuplicatedModuleException e2) {
                    MIBPARSINGLOGGERERROR.info(e2.getMessage());
                } catch (MibException.DuplicatedSymbolException e2) {
                    LogAdapter miblogger = LogAdapter.getLogger(MibStore.class.getName() + ".mib." + e2.getSymbol().module);
                    if (miblogger.isInfoEnabled()) {
                        miblogger.info(miblogger.getName() + e2.getMessage());
                    }
                } catch (MibException e2) {
                    MIBPARSINGLOGGERERROR.error(e2, e2.getMessage());
                }
            }
        });

    }

    public void load(InputStream... sources) throws IOException {
        try {
            Stream<CharStream>  antltrstream = Arrays.stream(sources)
                    .map( i-> {
                        try {
                            return CharStreams.fromStream(i);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            load(antltrstream);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public void load(Reader... sources) throws IOException {
        try {
            Stream<CharStream> antltrstream = Arrays.stream(sources)
                    .map(i -> {
                        try {
                            return CharStreams.fromReader(i);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            load(antltrstream);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public void load(String encoding, Path... sources) {
        Stream<CharStream> antltrstream = Arrays.stream(sources)
                .map(i -> {
                    String moduleencoding = encoding;
                    if (moduleencoding == null) {
                        String filename = i.getFileName().toString();
                        moduleencoding = encodings.getProperty(filename, "ASCII");
                    }
                    if ("skip".equals(moduleencoding)) {
                        return null;
                    }
                    try {
                        return CharStreams.fromPath(i, Charset.forName(moduleencoding));
                    } catch (IllegalCharsetNameException e) {
                        MIBPARSINGLOGGER.error("Invalid charset for %s: %s", i, moduleencoding);
                        return null;
                    } catch (IOException e) {
                        MIBPARSINGLOGGER.error("Invalid MIB source %s: %s", i, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull);
        load(antltrstream);
    }

    public void load(Path... sources) {
        load(null, sources);
    }

    public MibStore buildTree() {
        MIBPARSINGLOGGER.debug("Starting to build the MIB");
        MibStore newStore = new MibStoreImpl(top, modules, names, syntaxes, objects, resolvedTraps);

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
        MIBPARSINGLOGGER.debug("Creating the aliases for bad symbols");
        props.entrySet().iterator().forEachRemaining( i -> {
            Symbol bad = new Symbol(i.getKey().toString());
            Symbol good = new Symbol(i.getValue().toString());
            if (buildOids.containsKey(good) && ! badsymbols.contains(bad)) {
                MIBPARSINGLOGGERERROR.debug("adding invalid symbol mapping: %s -> %s" , bad, good);
                buildOids.put(bad, buildOids.get(good));
                badsymbols.add(bad);
            }
            if (types.containsKey(good) && ! badsymbols.contains(bad)) {
                MIBPARSINGLOGGERERROR.debug("adding invalid type declaration mapping: %s -> %s" , bad, good);
                types.put(bad, types.get(good));
            }
            if (textualConventions.containsKey(good) && ! badsymbols.contains(bad)) {
                MIBPARSINGLOGGERERROR.debug("adding invalid textual convention declaration mapping: %s -> %s" , bad, good);
                textualConventions.put(bad, textualConventions.get(good));
            }
        });
        MIBPARSINGLOGGER.debug("Sorting the OID");
        Set<Oid> sortedOid = sortdOids();
        allOids.clear();
        MIBPARSINGLOGGER.debug("Building the OID tree");
        sortedOid.forEach(oid -> {
            try {
                int[] content = oid.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                OidTreeNode node = top.add(content, oid.getName(), tableEntryOid.contains(oid));
                nodes.put(oid.getPath(buildOids), node);
                names.computeIfAbsent(oid.getName(), i -> new ArrayList<>()).add(node);
            } catch (MibException e) {
                try {
                    int[] content = oid.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                    top.add(content, oid.getName(), tableEntryOid.contains(oid));
                } catch (MibException e1) {
                    MIBPARSINGLOGGERERROR.error(e1, e1.getMessage());
                }
                MIBPARSINGLOGGERERROR.error(e, e.getMessage());
            }
        });

        MIBPARSINGLOGGERERROR.debug("Resolving the types");
        types.entrySet().stream()
        .filter(i -> i.getValue() != null)
        .forEach( i-> {
            try {
                i.getValue().resolve(types);
            } catch (MibException e) {
                MIBPARSINGLOGGERERROR.warn("Can't resolve type %s: %s", i.getKey(), e.getMessage());
            }
        });
        MIBPARSINGLOGGERERROR.debug("Resolving the textual conventions");
        resolveTextualConventions();
        types.forEach((i,j) -> syntaxes.put(i.name, j));
        MIBPARSINGLOGGERERROR.debug("Building the objects");
        Map<OidTreeNode, ObjectTypeBuilder> augmenters = new HashMap<>();
        buildObjects.forEach((k,v) -> {
            int[] components = null;
            OidTreeNode node = null;
            try {
                components = k.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray();
                node = top.find(components);
                if (v.isAugmenter()) {
                    augmenters.put(node, v);
                } else {
                    ObjectType object = v.resolve(this);
                    objects.put(node, object);
                }
            } catch (MibException e) {
                String objectname = null;
                if (node != null) {
                    objectname = node.getSymbol();
                } else {
                    objectname = "OID " + k;
                }
                MIBPARSINGLOGGERERROR.warn("Incomplete %s: %s", objectname, e.getMessage());
            }
        });
        augmenters.forEach((k,v) -> {
            Symbol augmented = v.getAugmented();
            Oid oidAugmented = buildOids.get(augmented);
            if (oidAugmented == null) {
                MIBPARSINGLOGGERERROR.warn("Unknown reference from %s: %s", k, augmented);
            } else {
                ObjectTypeBuilder builderAugmented = buildObjects.get(oidAugmented);
                try {
                    ObjectType object = v.resolve(this, builderAugmented);
                    objects.put(k, object);
                } catch (MibException e) {
                    MIBPARSINGLOGGERERROR.warn("Incomplete OID %s: %s", k, e.getMessage());
                }
            }
        });
        MIBPARSINGLOGGER.debug("Building the SNMPv1 traps");
        buildTraps.forEach((i,j) -> {
            try {
                Oid oid;
                if (i instanceof OidPath) {
                    OidPath p = (OidPath) i;
                    oid = new Oid(p.getRoot(), p.getComponents(), null);
                } else if (i instanceof Symbol) {
                    Symbol s =  (Symbol) i;
                    oid = buildOids.get(s);
                    if (oid == null) {
                        throw new MibException("Trap's enterprise unknown " + s);
                    }
                } else {
                    throw new MibException("Wrong enterprise type " + i.getClass().getName());
                }
                int[] oidPath;
                List<Integer> path = oid.getPath(buildOids);
                if (path == null) {
                    throw new MibException("Can't resolve path " + oid);
                }
                oidPath = path.stream().mapToInt(Integer::intValue).toArray();
                OidTreeNode node = top.find(oidPath);
                Map<Integer, Trap> traps = new HashMap<>(j.size());
                j.forEach((k,l) -> {
                    try {
                        traps.put(k, new Trap(l));
                    } catch (MibException e) {
                        MIBPARSINGLOGGERERROR.warn("Invalid trap: %s", e.getMessage());
                    }
                });
                resolvedTraps.computeIfAbsent(node, k -> new HashMap<>()).putAll(traps);
            } catch (MibException e1) {
                MIBPARSINGLOGGERERROR.warn("Invalid trap: %s", e1.getMessage());
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
                if (notDone.contains(s)){
                    Map<String, Object> attributes = e.getValue();
                    Syntax type = (Syntax) attributes.get("SYNTAX");
                    String hint = (String) attributes.get("DISPLAY-HINT");
                    try {
                        type.resolve(types);
                        TextualConvention tc = type.getTextualConvention(hint, type);
                        types.put(s, tc);
                    } catch (MibException.MissingSymbol ex) {
                        Syntax invalidType = type;
                        if (invalidType instanceof AnnotedSyntax) {
                            invalidType = ((AnnotedSyntax)invalidType).getSyntax();
                        }
                        if (invalidType instanceof Referenced) {
                            Symbol ref = ((Referenced)invalidType).getSymbol();
                            if (textualConventions.containsKey(ref)) {
                                // Not a real error, just a non processed textual convention
                                // To be tried again
                                continue;
                            }
                        }
                        MIBPARSINGLOGGERERROR.warn("Invalid textual convention %s: %s", s, ex.getMessage());
                    } catch (MibException | MibException.NonCheckedMibException ex) {
                        MIBPARSINGLOGGERERROR.warn("Invalid textual convention %s: %s", s, ex.getMessage());
                    }
                    notDone.remove(s);
                    resolvCount++;
                    if (resolvCount == textualConventions.size()) {
                        break;
                    }
                }
            }
        }
        if (notDone.isEmpty()) {
            MIBPARSINGLOGGERERROR.debug("missing textual convention %d", notDone.size());
        }
        // Replace some eventually defined TextualConvention with the smarter version
        Symbol dateAndTime = new Symbol("SNMPv2-TC", "DateAndTime");
        types.computeIfPresent(dateAndTime, (k, v) -> new TextualConvention.DateAndTime());
        Symbol displayString = new Symbol("SNMPv2-TC", "DisplayString");
        types.computeIfPresent(displayString, (k, v) -> new TextualConvention.DisplayString());
    }

    private Set<Oid> sortdOids() {
        Set<Oid> sortedoid = new TreeSet<>((o1, o2) -> {
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
        });

        allOids.forEach( i-> {
            // the table entry status needs to be memoried, the oid hash and equality is changed after getPath
            // So it needs to be put again in tableEntryOid
            boolean isTableEntry = MibLoader.this.tableEntryOid.contains(i);
            try {
                List<Integer> path = i.getPath(buildOids);
                if (! path.isEmpty()) {
                    Oid newi = new Oid(i.getPath(buildOids), i.getName());
                    sortedoid.add(newi);
                    if (isTableEntry) {
                        MibLoader.this.tableEntryOid.add(newi);
                    }
                } else {
                    MIBPARSINGLOGGERERROR.warn("Can't resolve OID %s path", i);
                }
            } catch (MibException | MibException.NonCheckedMibException e) {
                MIBPARSINGLOGGERERROR.warn("Can't add new OID %s: %s", i, e.getMessage());
                try {
                    if (! i.getPath(buildOids).isEmpty()) {
                        sortedoid.add(i);
                    }
                } catch (MibException e1) {
                    MIBPARSINGLOGGERERROR.warn("Second failure: can't add new OID %s: %s", i, e.getMessage());
                }
            }
            if (isTableEntry) {
                MibLoader.this.tableEntryOid.add(i);
            }
        });
        return sortedoid;
    }

    void resolve(Syntax syntax) throws MibException {
        syntax.resolve(types);
    }

    OidTreeNode resolveNode(Symbol s) {
        try {
            if (buildOids.containsKey(s)) {
                Oid o = buildOids.get(s);
                return nodes.get(o.getPath(buildOids));
            } else {
                return null;
            }
        } catch (MibException e) {
            MIBPARSINGLOGGERERROR.warn("Can't resolve node from symbol %s: %s", s, e.getMessage());
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


    void addMacroValue(Symbol s, OidPath value) throws MibException {
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

    public void addModuleIdentity(Symbol s, OidPath value) throws MibException {
        addOid(s, value, false);
    }

    public void addType(Symbol s, Syntax type) throws MibException {
        if (type instanceof NullSyntax) {
            return;
        }
        if (types.containsKey(s)) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        if (s == null || type == null) {
            throw new MibException("Empty settings " + s + " " + type);
        }
        types.put(s, type);
    }

    public void addValue(Symbol s, OidPath value) throws MibException {
        addOid(s, value, false);
    }

    private Oid addOid(Symbol s, OidPath p, boolean tableEntry) throws MibException {
        p.getAll(tableEntry).forEach( i-> {
            if (i.getName() != null &&  ! allOids.contains(i)) {
                allOids.add(i);
            }
        });
        Oid oid = new Oid(p.getRoot(), p.getComponents(), s.name);
        allOids.add(oid);
        if (tableEntry) {
            tableEntryOid.add(oid);
        }
        if (!buildOids.containsKey(s)) {
            buildOids.put(s, oid);
        } else {
            throw new MibException.DuplicatedSymbolException(s);
        }
        return oid;
    }

}
