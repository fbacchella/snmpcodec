package fr.jrds.snmpcodec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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
        if (allmibs) {
            try {
                Collections.list(Tasks.class.getClassLoader().getResources("allmibs.txt")).forEach( i -> {
                    try {
                        try(BufferedReader r = new BufferedReader(new InputStreamReader(i.openStream()))) {
                            String line;
                            while( (line = r.readLine()) != null) {
                                Path module = Paths.get(line);
                                String resolved = module.toAbsolutePath().normalize().toString().intern();
                                if (! done.contains(resolved)) {
                                    done.add(resolved);
                                    Tasks.loadpath(loader, module);
                                }
                            }
                        };
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                Arrays.stream(mibdirs)
                .map( i -> Paths.get(i))
                .forEach(i -> {
                    try {
                        String resolved = i.toAbsolutePath().normalize().toString().intern();
                        if (! done.contains(resolved)) {
                            done.add(resolved);
                            System.out.println(resolved);
                            Tasks.loadpath(loader, i);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
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
                return (file.toLowerCase().endsWith(".mib") || file.toLowerCase().endsWith(".txt") || file.toLowerCase().endsWith(".my"));
            }
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
        Collection<OidTreeNode> childs = level.childs();
        for (OidTreeNode i: childs) {
            count += 1 + countOid(i);
        }
        return count;
    }

}
