package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.junit.Test;

import fr.jrds.snmpcodec.LogUtils;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.Tasks;

public class ParserTest {

    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(OidTreeNodeTest.class, OidTreeNode.class.getName(), MibStore.class.getName());
    }

    @Test
    public void checkCodecs() throws URISyntaxException {
        MibStore store = Tasks.load(
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-CONF.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-MIB.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-SMI.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-TC.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()).toString()
                ).buildTree();
       // System.out.println(store.names);
//        Symbol tableSymbol = new Symbol("CUSTOM","table");
//
//        Syntax table = resolver.codecs.get(tableSymbol);
//        
//        Assert.assertFalse(resolver.codecs.containsKey(tableSymbol));
//        
//        System.out.println(resolver.codecs);
//        System.out.println(resolver.traps);
        Tasks.dumpNode(store.top);
    }

}
