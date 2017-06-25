package fr.jrds.snmpcodec;

import java.io.IOException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import fr.jrds.snmpcodec.objects.Symbol;

public class OidTreeNodeTest {

    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(OidTreeNodeTest.class, OidTreeNode.class.getName());
    }

    @Test
    public void manualfill() throws MibException {
        OidTreeNode top = new OidTreeNode();
        top.add(new int[] {1}, new Symbol("","iso"));
        top.add(new int[] {1, 1}, new Symbol("","std"));
        top.add(new int[] {1, 1, 8802}, new Symbol("","iso8802"));
        top.add(new int[] {1, 1, 8802, 1}, new Symbol("", "ieee802dot1"));
        top.add(new int[] {1, 1, 8802, 1, 1}, new Symbol("", "ieee802dot1mibs"));
        top.add(new int[] {1, 1, 8802, 1, 1, 1}, new Symbol("IEEE8021-PAE-MIB", "ieee8021paeMIB"));
        top.add(new int[] {1, 1, 8802, 1, 1, 1, 2}, new Symbol("IEEE8021-PAE-MIB", "dot1xPaeConformance"));
        top.add(new int[] {1, 2}, new Symbol("", "member-body"));
        Assert.assertEquals("1.2=::member-body", top.search(new int[] {1,2}).toString());
        Assert.assertEquals(null, top.search(new int[] {2}));
        Assert.assertEquals("1=::iso", top.search(new int[] {1,3}).toString());
        Assert.assertEquals("1.1.8802.1.1.1.2=IEEE8021-PAE-MIB::dot1xPaeConformance", top.search(new int[] {1, 1, 8802, 1, 1, 1, 2}).toString());
    }

}
