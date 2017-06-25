package fr.jrds.snmpcodec;

import java.text.ParseException;
import java.util.stream.IntStream;

import org.snmp4j.SNMP4JSettings;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.OIDTextFormat;
import org.snmp4j.util.VariableTextFormat;

import fr.jrds.snmpcodec.objects.DisplayHint;
import fr.jrds.snmpcodec.objects.SnmpType;
import fr.jrds.snmpcodec.objects.Symbol;

public class OIDFormatter implements OIDTextFormat, VariableTextFormat {

    private final MibStore resolver;
    private OIDTextFormat previous;
    private VariableTextFormat previousVar;

    public OIDFormatter(MibStore resolver) {
        this.resolver = resolver;
        previous = SNMP4JSettings.getOIDTextFormat();
        previousVar = SNMP4JSettings.getVariableTextFormat();
    }

    /**
     * Register in SNMP4J a default {@link MibTree}, it can be called many times
     * @return the new OIDFormatter
     */
    public static OIDFormatter register() {
        MibStore resolver = new MibStore();
        return register(resolver);
    }

    /**
     * Register in SNMP4J a custom  {@link MibTree}, it can be called many times
     * @param resolver the new OIDFormatter
     * @return the new OIDFormatter
     */
    public static OIDFormatter register(MibStore resolver) {
        OIDTextFormat previousTextFormat = SNMP4JSettings.getOIDTextFormat();
        VariableTextFormat previousVarFormat = SNMP4JSettings.getVariableTextFormat();
        OIDFormatter formatter = new OIDFormatter(resolver);
        SNMP4JSettings.setOIDTextFormat(formatter);
        SNMP4JSettings.setVariableTextFormat(formatter);
        if (previousTextFormat instanceof OIDFormatter) {
            formatter.previous = ((OIDFormatter) previousTextFormat).previous;
        }
        if (previousVarFormat instanceof OIDFormatter) {
            formatter.previousVar = ((OIDFormatter) previousTextFormat).previousVar;
        }
        return formatter;
    }

    /**
     * Added a new custom TextualConvention to the current mib base
     * @param clazz
     */
    public void addTextualConvention(Class<? extends DisplayHint> clazz) {
        resolver.addTextualConvention(clazz);
    }

    /**
     * Added a new TextualConvention described using a display hint string to the current mib base
     * @param name the name of the textual convention
     * @param displayHint, taken from the <code>DISPLAY-HINT</code> field from the <code>TEXTUAL-CONVENTION</code>.
     */
    public void addTextualConvention(String name, String displayHint) {
        resolver.addTextualConvention(name, displayHint);
    }

    @Override
    public String format(int[] value) {
        Object[] parsed = resolver.parseIndexOID(value);
        if(parsed != null && parsed.length > 0) {
            StringBuffer buffer = new StringBuffer(parsed[0].toString());
            IntStream.range(1, parsed.length).forEach(i -> buffer.append("[" + parsed[i] + "]"));
            return buffer.toString();
        } else {
            return previous.format(value);
        }
    }

    @Override
    public String formatForRoundTrip(int[] value) {
        return format(value);
    }

    @Override
    public int[] parse(String text) throws ParseException {
        try {
            Symbol s = new Symbol(text);
            if(resolver.containsKey(s)) {
                return resolver.getFromName(s);
            } else {
                return previous.parse(text);
            }
        } catch (MibException e) {
            throw new ParseException(e.getMessage(), 0);
        }

    }

    @Override
    public String format(OID instanceOID, Variable variable, boolean withOID) {
        String formatted = resolver.format(instanceOID, variable);
        if (formatted != null) {
            return formatted;
        } else {
            return previousVar.format(instanceOID, variable, withOID);
        }
    }

    @Override
    public VariableBinding parseVariableBinding(String text) throws ParseException {
        return previousVar.parseVariableBinding(text);
    }

    @Override
    public Variable parse(OID classOrInstanceOID, String text) throws ParseException {
        Variable v = resolver.parse(classOrInstanceOID, text);
        if (v != null) {
            return v;
        } else {
            return previousVar.parse(classOrInstanceOID, text);
        }
    }

    @Override
    public Variable parse(int smiSyntax, String text) throws ParseException {
        switch (smiSyntax) {
        // The natives BER types
        case SMIConstants.SYNTAX_INTEGER:
            return SnmpType.INTEGER.parse(text);
        case SMIConstants.SYNTAX_OCTET_STRING:
            return SnmpType.OctetString.parse(text);
        case SMIConstants.SYNTAX_NULL:
            return new org.snmp4j.smi.Null();
        case SMIConstants.SYNTAX_OBJECT_IDENTIFIER :
            return SnmpType.ObjID.parse( text);
            // The BER types declared in SNMPv2-SMI
        case SMIConstants.SYNTAX_IPADDRESS:
            return SnmpType.IpAddr.parse(text);
        case SMIConstants.SYNTAX_COUNTER32:
            return SnmpType.Counter32.parse(text);
        case SMIConstants.SYNTAX_GAUGE32:           // also matches SMIConstants.SYNTAX_UNSIGNED_INTEGER32
            return SnmpType.Gauge32.parse(text);
        case SMIConstants.SYNTAX_TIMETICKS:
            return SnmpType.TimeTicks.parse(text);
        case SMIConstants.SYNTAX_OPAQUE:
            return SnmpType.Opaque.parse(text);
        case SMIConstants.SYNTAX_COUNTER64 :
            return SnmpType.Counter64.parse(text);
        }
        return previousVar.parse(smiSyntax, text);
    }

}
