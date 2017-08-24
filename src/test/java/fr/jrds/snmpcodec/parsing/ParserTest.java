package fr.jrds.snmpcodec.parsing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
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
    public void checkCodecs() throws URISyntaxException, IOException {

        MibStore store = Tasks.load(false, 
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-CONF.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-MIB.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-SMI.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-TC.txt").toURI()).toString(),
                Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()).toString()
                ).buildTree();
        Assert.assertTrue(store.modules.contains("SNMPv2-CONF"));
        Assert.assertTrue(store.modules.contains("SNMPv2-SMI"));
        Assert.assertTrue(store.modules.contains("SNMPv2-TC"));
        Assert.assertTrue(store.modules.contains("CUSTOM"));
        Assert.assertTrue(store.modules.contains("SNMPv2-MIB"));
        Assert.assertEquals(5, store.modules.size());
        Assert.assertEquals(92, store.names.size());
        Assert.assertEquals(48, store.objects.size());
        Assert.assertEquals(0, store.resolvedTraps.size());
        Assert.assertEquals(33, store.syntaxes.size());
        int countOid = Tasks.countOid(store.top);
        Assert.assertEquals(92, countOid);

    }

    @Test
    public void testComment() throws Exception {

        Path module = Paths.get(getClass().getClassLoader().getResource("allcomments.txt").getFile());
        MibLoader loader = new MibLoader();
        loader.load(module);
        MibStore store = loader.buildTree();
        Assert.assertEquals(11, store.names.size());
        Assert.assertNotNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,1})); //oid1
        Assert.assertNotNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,2})); //oid2
        Assert.assertNotNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,3})); //oid3
        Assert.assertNotNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,4})); //oid4
        Assert.assertNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,5}));    //oid5
        Assert.assertNotNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,6})); //oid6
        Assert.assertNull(store.top.find(new int[]{1,3,6,1,4,1,1,1,7}));    //oid7

    }

}
