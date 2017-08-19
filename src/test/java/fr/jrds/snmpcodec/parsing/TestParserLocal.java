package fr.jrds.snmpcodec.parsing;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.junit.Test;

import fr.jrds.snmpcodec.LogUtils;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.Tasks;

public class TestParserLocal {

    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(OidTreeNodeTest.class, 
                OidTreeNode.class.getName(), MibStore.class.getName(), ModuleErrorListener.class.getName(),
                Tasks.class.getName());
        LogFactory.getLogger(ModuleErrorListener.class.getName()).setLogLevel(LogLevel.INFO);
    }

    @Test
    public void test1() throws Exception {
        if (System.getProperty("maven", "").equals("true")) {
            return;
        }
        org.antlr.v4.gui.TestRig.main(new String[] {
                "fr.jrds.snmpcodec.parsing.ASN",
                "fileContent",
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
        Path module = Paths.get(getClass().getClassLoader().getResource("ADAPTEC-UNIVERSAL-STORAGE-MIB.txt").getFile());
        MibLoader loader = new MibLoader();
        loader.load(module);
        MibStore store = loader.buildTree();
        Tasks.dumpNode(store.top);
    }

    @Test
    public void test3() throws Exception {
        MibStore store = Tasks.load(true).buildTree();
        int numoid = Tasks.countOid(store.top);
        int numnames = store.names.size();
        AtomicInteger duplicates = new AtomicInteger();
        store.names.values().stream().map( i -> i.size()).filter(i -> i > 1).forEach( i-> duplicates.addAndGet(i));
        int numduplicates = duplicates.get();
        System.out.format("%d differents OID, %d differents names, %d names collisions\n", numoid, numnames, numduplicates);
        store.names.entrySet().stream().filter(i -> false && i.getValue().size() > 1).forEach(i -> {
            System.out.println(i.getKey());
            i.getValue().forEach( j -> System.out.println("    " + j));
        });
    }

    //@Ignore
    @Test
    public void test4() throws Exception {
        MibStore store = Tasks.load(true).buildTree();

        BufferedInputStream bis = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream("alloid.txt"));
        try(BufferedReader d = new BufferedReader(new InputStreamReader(bis))){
            String line;
            while ((line = d.readLine()) != null) {
                line = line.substring(1);
                String[] parts = line.split("\\.");
                int[] oid = Arrays.stream(parts).mapToInt( i -> Integer.parseInt(i)).toArray();
                if (oid.length > 2 && (oid[oid.length - 1 ] == 0 || oid[oid.length - 2 ] == 0)) {
                    System.out.println("trap found");
                } else {
                    OidTreeNode node = store.top.find(oid);
                    if (node == null) {
                        System.out.println(line);
                    }
                }
            }
        }
    }
}
