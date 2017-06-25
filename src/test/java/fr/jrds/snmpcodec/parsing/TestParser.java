package fr.jrds.snmpcodec.parsing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import fr.jrds.snmpcodec.LogUtils;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.OidTreeNodeTest;

public class TestParser {
    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(OidTreeNodeTest.class, OidTreeNode.class.getName(), MibStore.class.getName());
    }

    @Test
    public void test1() throws Exception {
        if (System.getProperty("maven", "").equals("true")) {
            return;
        }
        org.antlr.v4.gui.TestRig.main(new String[] {
                "fr.jrds.snmpcodec.parsing.ASN",
                "moduleDefinition",
                "-tokens",
                "-diagnostics",
                "-ps","example1.ps",
                getClass().getClassLoader().getResource("ADAPTEC-UNIVERSAL-STORAGE-MIB.txt").getFile()
        });
    }

    @Test
    public void test2() throws Exception {
        if (System.getProperty("maven", "").equals("true")) {
            return;
        }
        Path mib = Paths.get(getClass().getClassLoader().getResource("ADAPTEC-UNIVERSAL-STORAGE-MIB.txt").getFile());
        MibStore store = new  MibStore();
        LoadMib loader = new LoadMib(store);
        loader.load(Collections.singleton(mib).stream());
        store.buildTree();
    }

    @Test
    public void test3() throws Exception {
        MibStore store = new  MibStore();
        LoadMib loader = new LoadMib(store);
        String[] defaults = new String[] {
                "/Users/fa4/Devl/SMI4J/src/main/resources/mibs/rfc",
                "/Users/fa4/Devl/SMI4J/src/main/resources/mibs/Sun",
                "/Users/fa4/Devl/SMI4J/src/main/resources/mibs/vmware",
                "/Users/fa4/Devl/SMI4J/src/main/resources/mibs/snmp4j",

        };
        String defaultval = Arrays.stream(defaults).collect(Collectors.joining(":"));
        String rootmibs = System.getProperty("MIBDIRS",defaultval);
        Arrays.stream(rootmibs.split(File.pathSeparator))
        .map( i -> Paths.get(i))
        .forEach(i -> {
            try {
                load(loader, i);
                store.buildTree();
            } catch (IOException e) {
                System.out.format("invalid path source: %s", i);
            } catch (ModuleException e) {
                System.out.format("broken mib: %s at %s\n", e.getMessage(), e.getLocation());
            } catch (Exception e) {
                System.out.format("broken mib: %s\n", i);
                e.printStackTrace(System.err);
            }
        });
        //System.out.println(store.traps);
        dumpNode(store.top);
        ///System.out.println(store.oids);
        //System.out.println(store.types);
    }
    
    private void dumpNode(OidTreeNode level) {
        Collection<OidTreeNode> childs = level.childs();
        //for (OidTreeNode i: childs) {
        //}
        for (OidTreeNode i: childs) {
            System.out.println(i);
            dumpNode(i);
        }
    }

    public static void load(LoadMib loader, Path mibs) throws IOException {
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
            case "rfc1228.txt":
            case "dpi20ref.txt":
            case "dpiSimple.mib":
            case "TEST-MIB.my":
                // bad mibs
            case "mibs_f5/F5-EM-MIB.txt":
            case "rfc/HPR-MIB.txt":
            case "test-mib-v1smi.my":   // Empty mib
            case "test-mib.my":         // Empty mib
            case "CISCO-OPTICAL-MONITORING-MIB.my": // Bad EOL
            case "CISCO-ATM-PVCTRAP-EXTN-CAPABILITY.my": //What is a VARIATION
            case "rfc1592.txt": //RFC with text
            case "rfc1227.txt": //RFC with text
            case "IBM-WIN32-MIB.mib": // A rfc with a lone \ before "
            case "view.my":      //Invalid comment, line 220:23
            case "CIMWIN32-MIB.mib": // Invalid escape sequence, line 1205:34
            case "IBMIROCAUTH-MIB.mib": // raw IP address, line 426:14
                return false;
            default:
                return (file.toLowerCase().endsWith(".mib") || file.toLowerCase().endsWith(".txt") || file.toLowerCase().endsWith(".my"));
            }
        };


        loader.load(Files.find(Paths.get(mibs.toUri()), 10, matcher));
    }

}
