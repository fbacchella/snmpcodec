package fr.jrds.snmpcodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.antlr.v4.runtime.misc.ParseCancellationException;

import fr.jrds.snmpcodec.MibException.DuplicatedModuleException;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.parsing.MibLoader;
import fr.jrds.snmpcodec.parsing.WrappedException;

public class Tasks {

    private final static LogAdapter logger = LogAdapter.getLogger(Tasks.class);

    public static MibLoader load(boolean allmibs, String... mibdirs) throws IOException {
        MibLoader loader = new MibLoader();
        Set<String> done = new HashSet<>();
        try {
            if (allmibs) {
                Collections.list(Tasks.class.getClassLoader().getResources("allmibs.txt"))
                        .forEach(i -> {
                    try (Stream<String> lines = Files.lines(Paths.get(i.toURI()))){
                       lines.filter(l -> !l.startsWith("#"))
                            .forEach(l -> {
                                 System.out.println(l);
                                 Path module = Paths.get(l);
                                 String resolved = module.toAbsolutePath().normalize().toString().intern();
                                 if (! done.contains(resolved)) {
                                     done.add(resolved);
                                     try {
                                         Tasks.loadpath(loader, module);
                                     } catch (IOException e) {
                                         throw new UncheckedIOException(e);
                                     }
                                 }
                             });
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    } catch (URISyntaxException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            Arrays.stream(mibdirs)
            .map(Paths::get)
            .forEach(i -> {
                try {
                    String resolved = i.toAbsolutePath().normalize().toString().intern();
                    if (! done.contains(resolved)) {
                        done.add(resolved);
                        Tasks.loadpath(loader, i);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return loader;
    }

    public static void loadpath(MibLoader loader, Path mibs) throws IOException {
        BiPredicate<Path,BasicFileAttributes> matcher = (i,j) -> {
            if (Files.isDirectory(i)) {
                return false;
            }
            String file = i.getFileName().toString();
            switch (file) {
            //Non mib files
            case "LICENSE.txt":
            case "README-MIB.txt":
            case "readme.txt":
            case "smux.txt":
            case "notifications.txt":
            case "Makefile.mib":
            case "dpi11.txt":
            case "GbE mib descriptions.txt":
            case "Gbe2 mib descriptions.txt":
            case "dpi20ref.txt": // Not a mib module
                /*case "dpiSimple.mib":
            case "TEST-MIB.my":*/
                // bad mibs
                //case "mibs_f5/F5-EM-MIB.txt":
                //case "rfc/HPR-MIB.txt":
                //case "test-mib-v1smi.my":   // Empty mib module
                //case "test-mib.my":         // Empty mib module
                //case "CISCO-OPTICAL-MONITORING-MIB.my": // Bad EOL
                //case "CISCO-ATM-PVCTRAP-EXTN-CAPABILITY.my": //What is a VARIATION
                //case "rfc1592.txt": //RFC with text
                //case "rfc1227.txt": //RFC with text
                //case "view.my":      //Invalid comment, line 220:23
            case "RFC-1215\nRFC1215\nRFC-1213.mib":   //What a name, invalid content anyway 
                return false;
            default:
                return (file.toLowerCase().endsWith(".mib") || file.toLowerCase().endsWith(".txt") || file.toLowerCase().endsWith(".my") || file.endsWith("-MIB") || file.endsWith("-SMI") || file.endsWith("-TC"));
            }
        };

        try (Stream<Path> paths = Files.find(Paths.get(mibs.toUri()), 10, matcher)) {
            paths.forEach(i -> {
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
    }

    public static void dumpNode(OidTreeNode level) {
        Collection<OidTreeNode> childs = level.childs();
        for (OidTreeNode i: childs) {
            i.getOID();
            i.getSymbol();
            System.out.println(i);
            dumpNode(i);
        }
    }

    public static int countOid(OidTreeNode level) {
        int count = 0;
        for (OidTreeNode i: level.childs()) {
            count += 1 + countOid(i);
        }
        return count;
    }

}
