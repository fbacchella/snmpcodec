package fr.jrds.snmpcodec;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.text.ParseException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;

import fr.jrds.snmpcodec.parsing.MibLoader;

public class Snmp4jTest {

    private String mibdirproperty;

    @BeforeClass
    static public void configure() throws IOException {
        LogUtils.setLevel(Snmp4jTest.class, MibStore.class.getName());
    }

    @Before
    public void saveEnv() {
        mibdirproperty = System.getProperty(OIDFormatter.MIBDIRSPROPERTY);
    }

    @After
    public void restoreEnv() {
        if (mibdirproperty != null) {
            System.setProperty(OIDFormatter.MIBDIRSPROPERTY,mibdirproperty);
        }
    }

    @Test
    public void testparseetree() throws InterruptedException, IOException, MibException, URISyntaxException, ParseException {

        MibLoader loader = new MibLoader();
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-CONF.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-MIB.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-SMI.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-TC.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()));

        MibStore resolver = loader.buildTree();
        OIDFormatter formatter = new OIDFormatter(resolver);

        SNMP4JSettings.setOIDTextFormat(formatter);
        SNMP4JSettings.setVariableTextFormat(formatter);

        Assert.assertEquals("iso", new OID("iso").format());
        Assert.assertEquals("sysDescr", new OID("sysDescr").format());
        Assert.assertEquals("enabled(1)", resolver.format(new OID("snmpEnableAuthenTraps"), new Integer32(1)));

        VariableBinding vb = new VariableBinding(new OID("snmpEnableAuthenTraps"), new Integer32(1));
        Assert.assertEquals("enabled(1)", vb.toValueString());

        PDUv1 trap = new PDUv1();

        trap.setType(PDU.V1TRAP);
        trap.setEnterprise(new OID("snmpcodec"));
        trap.setGenericTrap(PDUv1.ENTERPRISE_SPECIFIC);
        trap.setSpecificTrap(1);

        Assert.assertEquals("customTrap", formatter.format(new OID("snmpcodec"), new Integer32(1), true));
        Assert.assertEquals("sysORIndex[1]", new OID("sysORIndex.1").format());
        Assert.assertEquals("sysORID", new OID("sysORID").format());

    }

    @Test
    public void defaultRegister() throws URISyntaxException {
        String rfcmodules = getClass().getClassLoader().getResource("modules").toURI().getPath();
        String custommodule = getClass().getClassLoader().getResource("custommib.txt").toURI().getPath();
        System.setProperty(OIDFormatter.MIBDIRSPROPERTY, rfcmodules + File.pathSeparatorChar + custommodule);
        OIDFormatter.register();
        Assert.assertEquals("snmpcodec", new OID("snmpcodec").format());
    }

    @Test
    public void varfromFAQ1() throws ParseException, URISyntaxException {
        MibLoader loader = new MibLoader();
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-CONF.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-MIB.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-SMI.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-TC.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/IF-MIB.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/IANAifType-MIB.txt").toURI()));

        MibStore resolver = loader.buildTree();

        OIDFormatter formatter = new OIDFormatter(resolver);
        SNMP4JSettings.setOIDTextFormat(formatter);
        SNMP4JSettings.setVariableTextFormat(formatter);
        OID ifAdminStatus = new OID(resolver.getFromName("ifAdminStatus"));
        ifAdminStatus= ifAdminStatus.append(4);
        VariableBinding vbEnum = new VariableBinding(ifAdminStatus, "down(2)");
        Assert.assertEquals(new VariableBinding(new OID(new int[] { 1,3,6,1,2,1,2,2,1,7,4 }), new Integer32(2)), vbEnum);
        Assert.assertEquals("down(2)", vbEnum.toValueString());
    }

    @Test
    public void varFromFAQ2() throws ParseException, URISyntaxException {
        MibLoader loader = new MibLoader();
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/NOTIFICATION-LOG-MIB.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-SMI.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMPv2-TC.txt").toURI()));
        loader.load(Paths.get(getClass().getClassLoader().getResource("modules/SNMP-FRAMEWORK-MIB.txt").toURI()));

        MibStore resolver = loader.buildTree();

        OIDFormatter formatter = new OIDFormatter(resolver);
        SNMP4JSettings.setOIDTextFormat(formatter);
        SNMP4JSettings.setVariableTextFormat(formatter);

        OID nlmLogDateAndTime = new OID("nlmLogDateAndTime");
        nlmLogDateAndTime.append(1);
        VariableBinding vbDateAndTime = new VariableBinding(nlmLogDateAndTime,"2015-10-13,12:45:53.8,+2:0");
        Assert.assertEquals(new VariableBinding(new OID(new int[] { 1,3,6,1,2,1,92,1,3,1,1,3,1 }), OctetString.fromHexString("07:df:0a:0d:0c:2d:35:08:2b:02:00")), vbDateAndTime);
    }

}
