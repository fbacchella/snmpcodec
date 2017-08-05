package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.log.LogAdapter;


public class MibLoader {

    final LogAdapter logger = LogAdapter.getLogger(MibLoader.class);

    private final ModuleListener modulelistener;
    private final ANTLRErrorListener errorListener = new ModuleErrorListener();


    public MibLoader(MibStore store) {
        super();
        modulelistener = new ModuleListener(store);
    }

    private void load(Stream<ANTLRInputStream> source) {
        source
        .map(i -> new ASNLexer(i))
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
            } catch (ModuleException e) {
                System.err.println(e.getLocation());
                return null;
            }
        })
        .filter(i -> i != null)
        .forEach(i -> {
            try {
                ParseTreeWalker.DEFAULT.walk(modulelistener, i);
            } catch (ModuleException.DuplicatedMibException e) {
                //logger.debug("Duplicated module '%s' at '%s'" , e.getModule(), e.getLocation());
            } catch (ModuleException e) {
                logger.error("%s at %s" , e.getMessage(), e.getLocation());
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

    public void load(Path... sources) {
        Stream<ANTLRInputStream> antltrstream = Arrays.stream(sources)
                .map(i -> i.toString())
                .map(i -> {
                    try {
                        return new ANTLRFileStream(i);
                    } catch (IOException e) {
                        logger.error("Invalid MIB source %s: %s", i, e.getMessage());
                        return null;
                    }
                })
                .filter( i-> i != null).map(i -> (ANTLRInputStream) i);
        load(antltrstream);
    }

}
