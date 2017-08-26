package fr.jrds.snmpcodec;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.ParseException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

import fr.jrds.snmpcodec.parsing.MibLoader;

public class Snmp4jTest {

    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(Snmp4jTest.class, MibStore.class.getName());
    }

    @Test
    public void testparseetree() throws InterruptedException, IOException, MibException, URISyntaxException, ParseException {

        MibLoader loader = new MibLoader();
        loader.load(Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-CONF.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-MIB.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-SMI.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("rfc-modules/SNMPv2-TC.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()));

        MibStore resolver = loader.buildTree();

        OIDFormatter formatter = new OIDFormatter(resolver);
        SNMP4JSettings.setOIDTextFormat(formatter);
        SNMP4JSettings.setVariableTextFormat(formatter);

        Assert.assertEquals("iso", new OID("iso").format());
        Assert.assertEquals("sysDescr", new OID("sysDescr").format());
        Assert.assertEquals("enabled", resolver.format(new OID("snmpEnableAuthenTraps"), new Integer32(1)));

        VariableBinding vb = new VariableBinding(new OID("snmpEnableAuthenTraps"), new Integer32(1));
        Assert.assertEquals("enabled", vb.toValueString());

        PDUv1 trap = new PDUv1();

        trap.setType(PDU.V1TRAP);
        trap.setEnterprise(new OID("snmpcodec"));
        trap.setGenericTrap(PDUv1.ENTERPRISE_SPECIFIC);
        trap.setSpecificTrap(1);

        Assert.assertEquals("customTrap", formatter.format(new OID("snmpcodec"), new Integer32(1), true));
        Assert.assertEquals("sysORIndex[1]", new OID("sysORIndex.1").format());
        Assert.assertEquals("sysORID", new OID("sysORID").format());

    }

}
