package fr.jrds.snmpcodec;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.smi.OID;
import org.snmp4j.util.OIDTextFormat;

import fr.jrds.snmpcodec.parsing.MibLoader;

public class OIDFormatterTest {

    private static OIDTextFormat state;

    @BeforeClass
    public static void saveState() {
        state = SNMP4JSettings.getOIDTextFormat();
    }

    @AfterClass
    public static void restoreState() {
        SNMP4JSettings.setOIDTextFormat(state);
    }

    @Test
    public void chaining() {
        SNMP4JSettings.setOIDTextFormat(new OIDTextFormat() {

            @Override
            public String format(int[] value) {
                return null;
            }

            @Override
            public String formatForRoundTrip(int[] value) {
                return null;
            }

            @Override
            public int[] parse(String text) {
                throw new IllegalStateException("Empty parser");
            }
        });
        MibStore store = new MibLoader().buildTree();
        Assert.assertTrue(store.isEmpty());
        OIDFormatter.register(store);
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class, ()-> new OID("empty"));
        Assert.assertEquals("Empty parser", ex.getMessage());
    }

}
