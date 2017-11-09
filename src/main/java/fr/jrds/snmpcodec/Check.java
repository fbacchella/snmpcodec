package fr.jrds.snmpcodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.snmp4j.log.ConsoleLogFactory;
import org.snmp4j.log.LogFactory;
import org.snmp4j.log.LogLevel;

import fr.jrds.snmpcodec.MibException.DuplicatedModuleException;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.parsing.MibLoader;
import fr.jrds.snmpcodec.parsing.WrappedException;

public class Check {

    private final static LogAdapter logger = LogAdapter.getLogger(Check.class);

    public static void main(String[] args) throws IOException {
        LogFactory.setLogFactory(new ConsoleLogFactory());
        LogAdapter logger = LogAdapter.getLogger("fr.jrds.snmpcodec");
        logger.setLogLevel(LogLevel.WARN);

        MibStore store = load(args).buildTree();
        int numoid = countOid(store.top);
        int numnames = store.names.size();
        AtomicInteger duplicates = new AtomicInteger();
        store.names.values().stream().map( i -> i.size()).filter(i -> i > 1).forEach( i-> duplicates.addAndGet(i));
        int numduplicates = duplicates.get();
        System.out.format("%d differents OID, %d differents names, %d names collisions\n", numoid, numnames, numduplicates);
        AtomicInteger trapsCount = new AtomicInteger(1);
        store.resolvedTraps.forEach((i,j) -> trapsCount.addAndGet(j.size()));
        System.out.format("%d v1 trap, %d values\n", store.resolvedTraps.size(), trapsCount.get());
        System.out.format("%d modules\n", store.modules.size());
    }

    private static MibLoader load(String[] args) throws IOException {
        try {
            MibLoader loader = new MibLoader();
            Arrays.stream(args)
            .map( i-> Paths.get(i))
            .forEach(i -> {
                try {
                    loadpath(loader, i);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return loader;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static void loadpath(MibLoader loader, Path mibs) throws IOException {
        BiPredicate<Path,BasicFileAttributes> matcher = (i,j) -> {
            if (Files.isDirectory(i)) {
                return false;
            }
            String file = i.getFileName().toString();
            return (file.toLowerCase().endsWith(".mib") || file.toLowerCase().endsWith(".txt") || file.toLowerCase().endsWith(".my"));
        };

        Files.find(Paths.get(mibs.toUri()), 10, matcher).forEach(i -> {
            try {
                loader.load(i);
            } catch (WrappedException e) {
                try {
                    throw e.getRootException();
                } catch (DuplicatedModuleException e1) {
                    // Many of them, ignore
                } catch (Exception e1) {
                    logger.error("broken module: %s at %s\n", e.getMessage(), e.getLocation());
                }
            } catch (ParseCancellationException e) {
                logger.error("Broken module %s: %s\n", i, e.getMessage());
            } catch (Exception e) {
                logger.error("Broken module %s parsing : %s\n", i, e.getMessage());
                e.printStackTrace(System.err);
            }

        });
    }

    private static int countOid(OidTreeNode level) {
        int count = 0;
        Collection<OidTreeNode> childs = level.childs();
        for (OidTreeNode i: childs) {
            count += 1 + countOid(i);
        }
        return count;
    }

}
