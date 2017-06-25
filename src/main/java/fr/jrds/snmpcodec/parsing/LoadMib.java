package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.objects.DeclaredType;
import fr.jrds.snmpcodec.objects.Oid;
import fr.jrds.snmpcodec.objects.Symbol;
import fr.jrds.snmpcodec.parsing.ModuleListener.StructuredObject;


public class LoadMib {

    public final Map<String, String> displayHints = new HashMap<>();
    public final Map<String, Map<String, Object>> textualConventions = new HashMap<>();
    public final Map<Number, StructuredObject<?>> trapstype = new HashMap<>();
    public final Map<Symbol, DeclaredType<?>> types = new Symbol.SymbolMap();
    public final Map<Symbol, Object> assigned = new HashMap<>();

    private final  MibStore store;


    public LoadMib(MibStore store) {
        super();
        this.store = store;
    }

    public void load(Stream<Path> source) throws IOException {
        ModuleListener modulelistener = new ModuleListener(store);
        ANTLRErrorListener errorListener = new ModuleErrorListener();
        source
        .map(i -> i.toString())
        .map(i -> {
            try {
                return new ANTLRFileStream(i);
            } catch (IOException e) {
                return null;
            }
        })
        .filter(i -> i != null)
        .map(i -> new ASNLexer(i))
        .map(i -> new CommonTokenStream(i))
        .map(i -> {
            ASNParser parser = new ASNParser(i);
            parser.removeErrorListeners();
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            modulelistener.parser = parser;
            return parser;
        })
        .map(i -> {
            try {
                return i.moduleDefinition();
            } catch (ModuleException e) {
                System.err.println(e.getLocation());
                return null;
            }
        })
        .filter(i -> i != null)
        .forEach(i -> {
            try {
                ParseTreeWalker.DEFAULT.walk(modulelistener, i);
            } catch (ModuleException e) {
                System.err.println(e.getMessage() + " at " + e.getLocation());
            }
        });
    }

}
