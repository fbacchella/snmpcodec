package fr.jrds.snmpcodec.smi;

import org.junit.Assert;
import org.junit.Test;

public class SymbolTest {

    @Test
    public void testEqualDual() {
        Symbol s1 = new Symbol("SNMPv2-MIB", "snmpMIB");
        Symbol s2 = new Symbol("SNMPv2-MIB", "snmpMIB");
        Symbol s3 = new Symbol("SNMPv2-MIB.snmpMIB");
        Assert.assertTrue(s1.equals(s2));
        Assert.assertTrue(s2.equals(s1));
        Assert.assertTrue(s3.equals(s1));
        Assert.assertEquals(s1.hashCode(), s2.hashCode());
        Assert.assertEquals(s1.hashCode(), s3.hashCode());
    }

    @Test
    public void testEqualSingle() {
        Symbol s1 = new Symbol("iso");
        Symbol s2 = new Symbol("iso");
        Assert.assertTrue(s1.equals(s2));
        Assert.assertTrue(s2.equals(s1));
        Assert.assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    public void testDifferentDual() {
        Symbol s1 = new Symbol("SNMPv2-MIB", "snmpMIB");
        Symbol s2 = new Symbol("ccitt");
        Assert.assertFalse(s1.equals(s2));
        Assert.assertFalse(s2.equals(s1));
        Assert.assertNotEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    public void testDifferentSingle() {
        Symbol s1 = new Symbol("iso");
        Symbol s2 = new Symbol("ccitt");
        Assert.assertFalse(s1.equals(s2));
        Assert.assertFalse(s2.equals(s1));
        Assert.assertNotEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    public void testtoString() {
        Symbol s1 = new Symbol("SNMPv2-MIB", "snmpMIB");
        Assert.assertEquals("SNMPv2-MIB.snmpMIB", s1.toString());
        Symbol s2 = new Symbol("ccitt");
        Assert.assertEquals("ccitt", s2.toString());
    }

}
