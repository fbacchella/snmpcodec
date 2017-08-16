package fr.jrds.snmpcodec.parsing;

import java.io.IOException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import fr.jrds.snmpcodec.LogUtils;
import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.OidTreeNode;

public class OidTreeNodeTest {

    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(OidTreeNodeTest.class, OidTreeNode.class.getName());
    }

    @Test
    public void manualfill() throws MibException {
        OidTreeNodeImpl top = new OidTreeNodeImpl();
        top.add(new int[] {1}, "iso", false);
        top.add(new int[] {1, 1}, "std", false);
        top.add(new int[] {1, 1, 8802}, "iso8802", false);
        top.add(new int[] {1, 1, 8802, 1}, "ieee802dot1", false);
        top.add(new int[] {1, 1, 8802, 1, 1}, "ieee802dot1mibs", false);
        top.add(new int[] {1, 1, 8802, 1, 1, 1}, "ieee8021paeMIB", false);
        top.add(new int[] {1, 1, 8802, 1, 1, 1, 2}, "dot1xPaeConformance", false);
        top.add(new int[] {1, 2}, "member-body", false);
        Assert.assertEquals("1.2=member-body", top.search(new int[] {1,2}).toString());
        Assert.assertEquals(null, top.search(new int[] {2}));
        Assert.assertEquals("1=iso", top.search(new int[] {1,3}).toString());
        Assert.assertEquals("1.1.8802.1.1.1.2=dot1xPaeConformance", top.search(new int[] {1, 1, 8802, 1, 1, 1, 2}).toString());
    }

    @Test
    public void fillWithHole() throws MibException {
        OidTreeNodeImpl top = new OidTreeNodeImpl();
        top.add(new int[] {1}, "iso", false);
        top.add(new int[] {1, 1}, "std", false);
        top.add(new int[] {1, 1, 8802}, "iso8802", false);
        top.add(new int[] {1, 1, 8802, 1}, "ieee802dot1", false);
        top.add(new int[] {1, 1, 8802, 1, 1, 1, 2}, "dot1xPaeConformance", false);
        top.add(new int[] {1, 2}, "member-body", false);
        Assert.assertEquals("1.2=member-body", top.search(new int[] {1,2}).toString());
        Assert.assertEquals(null, top.search(new int[] {2}));
        Assert.assertEquals("1=iso", top.search(new int[] {1,3}).toString());
        Assert.assertEquals("1.1.8802.1.1.1.2=dot1xPaeConformance", top.search(new int[] {1, 1, 8802, 1, 1, 1, 2}).toString());
    }
}
