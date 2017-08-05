package fr.jrds.snmpcodec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import fr.jrds.snmpcodec.parsing.MibLoader;

public class Tasks {

    public static void load(MibStore resolver, String... mibdirs) {
        MibLoader loader = new MibLoader(resolver);
        String defaultval = Arrays.stream(mibdirs).collect(Collectors.joining(":"));
        String rootmibs = System.getProperty("MIBDIRS",defaultval);
        Arrays.stream(rootmibs.split(File.pathSeparator))
        .map( i -> Paths.get(i))
        .forEach(i -> {
            try {
                Tasks.loadpath(loader, i);
            } catch (IOException e) {
                System.out.format("invalid path source: %s", i);
                //            } catch (ModuleException.DuplicatedMibException e) {
                //                //System.out.format("broken mib: %s at %s\n", e.getMessage(), e.getLocation());
                //            } catch (ModuleException e) {
                //                System.out.format("broken mib: %s at %s\n", e.getMessage(), e.getLocation());
            } catch (Exception e) {
                System.out.format("broken mib: %s\n", i);
                e.printStackTrace(System.err);
            }
        });
        resolver.buildTree();

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
            case "rfc1228.txt":  // The raw RFC
            case "dpi20ref.txt": // Not a mib module
            case "dpiSimple.mib":
            case "TEST-MIB.my":
                // bad mibs
                //case "mibs_f5/F5-EM-MIB.txt":
                //case "rfc/HPR-MIB.txt":
                //case "test-mib-v1smi.my":   // Empty mib module
            case "test-mib.my":         // Empty mib module
            case "CISCO-OPTICAL-MONITORING-MIB.my": // Bad EOL
            case "CISCO-ATM-PVCTRAP-EXTN-CAPABILITY.my": //What is a VARIATION
            case "rfc1592.txt": //RFC with text
            case "rfc1227.txt": //RFC with text
            case "view.my":      //Invalid comment, line 220:23
            case "RFC-1215\nRFC1215\nRFC-1213.mib":   //What a name, invalid content anyway 
                return false;
            default:
                return (file.toLowerCase().endsWith(".mib") || file.toLowerCase().endsWith(".txt") || file.toLowerCase().endsWith(".my"));
            }
        };

        Files.find(Paths.get(mibs.toUri()), 10, matcher).forEach(i -> loader.load(i));
    }


}
