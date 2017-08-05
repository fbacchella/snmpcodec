package fr.jrds.snmpcodec.smi;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.smi.TextualConvention.PatternDisplayHint;
import fr.jrds.snmpcodec.smi.TextualConvention.Unsigned32DisplayHint;
import fr.jrds.snmpcodec.smi.TextualConvention.Signed32DisplayHint;

public class TextualConventionTest {

    @SuppressWarnings("rawtypes")
    private void testhint(Symbol s, String displayHint, Variable v, String expected) {
        Map<Symbol, TextualConvention> annotations = new HashMap<>();
        if (v instanceof UnsignedInteger32) {
            annotations.put(s, new Unsigned32DisplayHint(SmiType.Unsigned32, displayHint));
        } else if (v instanceof Integer32) {
            annotations.put(s, new Signed32DisplayHint(SmiType.INTEGER, displayHint));
        } else {
            annotations.put(s, new PatternDisplayHint(SmiType.OctetString, displayHint, null));
        }
        Assert.assertEquals(expected, annotations.get(s).format(v));

    }
    @Test
    public void testpattern() {
        testhint(new Symbol("T11FabricIndex"), "d", new UnsignedInteger32(10), "10");
        testhint(new Symbol("L2tpMilliSeconds"), "d-3", new Integer32(1234), "1.234");
        testhint(new Symbol("L2tpMilliSeconds"), "d-3", new Integer32(123), ".123");
        testhint(new Symbol("L2tpMilliSeconds"), "d-3", new Integer32(12), ".012");
        testhint(new Symbol("L2tpMilliSeconds"), "d-3", new Integer32(1), ".001");
        testhint(new Symbol("T11ZsZoneMemberType"), "x", new UnsignedInteger32(10), "a");
        testhint(new Symbol("Ipv6AddressPrefix"), "2x", new OctetString(new byte[]{(byte)255,(byte)255}), "ffff");
        testhint(new Symbol("MplsLdpIdentifier"), "1d.1d.1d.1d:2d:", new OctetString(new byte[]{(byte)1,(byte)2,(byte)3,(byte)4,(byte)5,(byte)6}), "1.2.3.4:1286:");
    }

    @Test
    public void test3() {
        Map<Number, String> names = new HashMap<>();
        names.put(1, "other");
        names.put(2, "volatile");
        names.put(3, "nonVolatile");
        names.put(4, "permanent");
        names.put(5, "readOnly");
        Syntax syntax = new IndirectSyntax(SmiType.INTEGER, names, null);
        TextualConvention tc = SmiType.INTEGER.getTextualConvention(null, syntax);
        Assert.assertEquals(new Integer32(3), tc.parse("nonVolatile"));
        Assert.assertEquals(new Integer32(2), tc.parse("2"));
    }

    @Test
    public void test4() {
        TextualConvention tc = SmiType.Counter64.getTextualConvention("d", SmiType.Counter64);
        Assert.assertEquals(new Counter64(2), tc.parse("2"));
    }

}
