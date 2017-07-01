package fr.jrds.snmpcodec.smi;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;

public class DisplayHintTest {

    private void test(Symbol s, String displayHint, Variable v, String expected) {
        Map<Symbol, DisplayHint> annotations = new HashMap<>();
        DisplayHint.addAnnotation(s, displayHint, annotations);
        Assert.assertEquals(expected, annotations.get(s).format(v));

    }
    @Test
    public void test1() {
        test(new Symbol("T11FabricIndex"), "d", new OctetString(new byte[]{10}), "10");
    }

    @Test
    public void test2() {
        test(new Symbol("T11ZsZoneMemberType"), "x", new OctetString(new byte[]{10}), "a");
    }

    @Test
    public void test3() {
        test(new Symbol("Ipv6AddressPrefix"), "2x", new OctetString(new byte[]{(byte)255,(byte)255}), "ffff");
    }

    @Test
    public void test4() {
        test(new Symbol("MplsLdpIdentifier"), "1d.1d.1d.1d:2d:", new OctetString(new byte[]{(byte)1,(byte)2,(byte)3,(byte)4,(byte)5,(byte)6}), "1.2.3.4:1286:");
    }

}
