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

import fr.jrds.snmpcodec.MibException.DuplicatedSymbolOid;
import fr.jrds.snmpcodec.MibException.NonCheckedMibException;
import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.IndirectSyntax;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Oid;
import fr.jrds.snmpcodec.smi.ProvidesTextualConvention;
import fr.jrds.snmpcodec.smi.Referenced;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.TextualConvention;


public class MibLoader {

    final LogAdapter logger = LogAdapter.getLogger(MibLoader.class);

    private final ModuleListener modulelistener;
    private final ANTLRErrorListener errorListener = new ModuleErrorListener();
    private final Properties encodings;


    private Set<Oid> allOids = new HashSet<>();
    private final Set<Symbol> badsymbols = new HashSet<>();
    private final Map<Symbol, Oid> buildOids = new HashMap<>();
    private final Map<Symbol, Syntax> buildSyntaxes = new HashMap<>();
    private final Map<Symbol, Map<Integer, Map<String, Object>>> buildTraps = new HashMap<>();
    private final Map<Symbol, Map<String, Object>> textualConventions = new HashMap<>();
    private final Map<Oid, ObjectTypeBuilder> buildObjects = new HashMap<>();
    private final Set<Symbol> symbols = new HashSet<>();
    private final Map<Symbol, OidTreeNode> resolvedOids = new HashMap<>();
    private final OidTreeNodeImpl top = new OidTreeNodeImpl();
    private final Map<String, List<OidTreeNode>> names = new HashMap<>();
    private final Set<String> modules = new HashSet<>();

    private Map<OidTreeNode, Syntax> _syntaxes = new HashMap<>();
    private Map<OidTreeNode, ObjectType> _objects = new HashMap<>();
    private Map<OidTreeNode, Map<Integer, Map<String, Object>>> _resolvedTraps = new HashMap<>();
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
                return i.moduleDefinition();
            } catch (WrappedException e) {
                logger.error("Not a valid module: " + e.getMessage() + " "+ e.getLocation());
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
                    logger.error(e2.getMessage());
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
                        logger.error("Invalid MIB source %s: %s", i, e.getMessage());
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
                    top.add(content, oid.getName(), oid.isTableEntry());
                } catch (MibException e1) {
                    System.out.println(e.getMessage());
                }
                System.out.println(e.getMessage());
            }
        });
        buildObjects.forEach((k,v) -> {
            OidTreeNode node;
            try {
                node = top.find(k.getPath(buildOids).stream().mapToInt(Integer::intValue).toArray());
                _objects.put(node, v.resolve(this, node));
            } catch (MibException e) {
                System.out.println(e.getMessage());
            }
        });
        newStore = new MibStoreImpl(top, modules, names, _syntaxes, _objects, _resolvedTraps);
        this.buildSyntaxes.forEach((k, v) -> {
            OidTreeNode node = resolvedOids.get(k);
            _syntaxes.put(node, checkSyntax(node, v));
        });
        resolveTextualConventions(newStore, _syntaxes);
        return newStore;
    }

    private void resolveTextualConventions(MibStore store, Map<OidTreeNode, Syntax> _syntaxes) {
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
                        ref.resolve(refNode, store);
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
                attributes.remove("DESCRIPTION");
                System.out.println("Invalid textual convention " + s + " " + attributes);
            }
        });
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

    Syntax checkSyntax(OidTreeNode node, Syntax syntax) {
        if (syntax instanceof Referenced) {
            Referenced ref = (Referenced) syntax;
            Symbol refSymbol = ref.getSymbol();
            if (refSymbol != null) {
                OidTreeNode refNode = resolvedOids.get(ref.getSymbol());
                ref.resolve(refNode, newStore);
            }
        }
        return syntax;
    }

    OidTreeNode resolveNode(Symbol s) {
        return resolvedOids.get(s);
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
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        addOid(s, value, false);
    }

    void addTrapType(Symbol s, String name, Map<String, Object> attributes, Number trapIndex) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        attributes.put("SYMBOL", s);
        Object enterprise = attributes.get("ENTERPRISE");
        if (enterprise instanceof Symbol) {
            buildTraps.computeIfAbsent((Symbol)enterprise, k -> new HashMap<>()).put(trapIndex.intValue(), attributes);
        }
    }

    void addObjectType(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        ObjectTypeBuilder newtype = new ObjectTypeBuilder(attributes);
        Oid oid = addOid(s, value, newtype.isIndexed());
        buildObjects.put(oid, newtype);
    }

    public void addTextualConvention(Symbol s, Map<String, Object> attributes) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        textualConventions.put(s, attributes);
        symbols.add(s);
    }

    public void addModuleIdentity(Symbol s, Map<String, Object> attributes, OidPath value) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        addOid(s, value, false);
    }

    public void addType(Symbol s, Syntax type) throws MibException {
        if (symbols.contains(s) ) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        buildSyntaxes.put(s, type);
        symbols.add(s);
    }

    public void addValue(Symbol s, Syntax syntax, OidPath value) throws MibException {
        if (symbols.contains(s)) {
            throw new MibException.DuplicatedSymbolException(s);
        }
        addOid(s, (OidPath)value, false);
    }

    private Oid addOid(Symbol s, OidPath p, boolean tableEntry) throws MibException {
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
        return oid;
    }

}
