package fr.jrds.snmpcodec.smi;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.log.LogFactory;
import org.snmp4j.log.LogLevel;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OIDFormatter;
import fr.jrds.snmpcodec.Tasks;
import fr.jrds.snmpcodec.parsing.ModuleErrorListener;

public class IndexTest {

    static MibStore resolver;

    @BeforeClass
    static public void configure() throws IOException {
        LogFactory.getLogger(ModuleErrorListener.class.getName()).setLogLevel(LogLevel.WARN);

        resolver = Tasks.load(false, "/usr/share/snmp/mibs").buildTree();
    }

    @Test
    public void oidfromNetSnmpFAQ() {
        OID vacmAccessContextMatch = new OID("1.3.6.1.6.3.16.1.4.1.4.7.118.51.103.114.111.117.112.0.3.1");
        Map<String, Object> parts = resolver.parseIndexOID(vacmAccessContextMatch.getValue());
        Assert.assertEquals("vacmAccessContextMatch", parts.get("vacmAccessTable"));
        Assert.assertEquals("v3group", parts.get("vacmGroupName"));
        Assert.assertEquals("", parts.get("vacmAccessContextPrefix"));
        Assert.assertEquals(3, parts.get("vacmAccessSecurityModel"));
        Assert.assertEquals("noAuthNoPriv", parts.get("vacmAccessSecurityLevel"));
    }

    @Test
    public void varfromFAQ() throws ParseException {
        OIDFormatter formatter = new OIDFormatter(resolver);
        SNMP4JSettings.setOIDTextFormat(formatter);
        SNMP4JSettings.setVariableTextFormat(formatter);

        OID ifAdminStatus = new OID(resolver.getFromName("ifAdminStatus")).append(4);
        VariableBinding vbEnum = new VariableBinding(ifAdminStatus, "down(2)");
        Assert.assertEquals(new VariableBinding(new OID(new int[] { 1,3,6,1,2,1,2,2,1,7,4 }), new Integer32(2)), vbEnum);
        Assert.assertEquals("down(2)", vbEnum.toValueString());
        OID nlmLogDateAndTime = new OID("nlmLogDateAndTime");
        nlmLogDateAndTime.append(1);
        VariableBinding vbDateAndTime = new VariableBinding(nlmLogDateAndTime,"2015-10-13,12:45:53.8,+2:0");
        Assert.assertEquals(new VariableBinding(new OID(new int[] { 1,3,6,1,2,1,92,1,3,1,1,3,1 }), OctetString.fromHexString("07:df:0a:0d:0c:2d:35:08:2b:02:00")), vbDateAndTime);
    }

    @Test
    public void testWithIp() throws UnknownHostException {
        OID udpLocalAddress = new OID("1.3.6.1.2.1.7.5.1.1.0.0.0.0.123");
        Map<String, Object> parts = resolver.parseIndexOID(udpLocalAddress.getValue());
        Assert.assertEquals("udpLocalAddress", parts.get("udpTable"));
        Assert.assertEquals(Inet4Address.getByAddress(new byte[]{0, 0, 0, 0}), parts.get("udpLocalAddress"));
        Assert.assertEquals(123, parts.get("udpLocalPort"));
    }

}
