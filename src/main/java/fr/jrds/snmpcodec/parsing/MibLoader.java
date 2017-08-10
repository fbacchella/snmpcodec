package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import fr.jrds.snmpcodec.MibException.NonCheckedMibException;
import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.log.LogAdapter;


public class MibLoader {

    final LogAdapter logger = LogAdapter.getLogger(MibLoader.class);

    private final ModuleListener modulelistener;
    private final ANTLRErrorListener errorListener = new ModuleErrorListener();
    private final Properties encodings;


    public MibLoader(MibStore store) {
        super();
        modulelistener = new ModuleListener(store);

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
                System.err.println(e.getMessage() + " "+ e.getLocation());
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
}
