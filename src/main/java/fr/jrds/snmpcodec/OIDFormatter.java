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

import fr.jrds.snmpcodec.smi.SmiType;

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
            if(resolver.containsKey(text)) {
                return resolver.getFromName(text);
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
            return SmiType.INTEGER.parse(text);
        case SMIConstants.SYNTAX_OCTET_STRING:
            return SmiType.OctetString.parse(text);
        case SMIConstants.SYNTAX_NULL:
            return new org.snmp4j.smi.Null();
        case SMIConstants.SYNTAX_OBJECT_IDENTIFIER :
            return SmiType.ObjID.parse( text);
            // The BER types declared in SNMPv2-SMI
        case SMIConstants.SYNTAX_IPADDRESS:
            return SmiType.IpAddr.parse(text);
        case SMIConstants.SYNTAX_COUNTER32:
            return SmiType.Counter32.parse(text);
        case SMIConstants.SYNTAX_GAUGE32:           // also matches SMIConstants.SYNTAX_UNSIGNED_INTEGER32
            return SmiType.Gauge32.parse(text);
        case SMIConstants.SYNTAX_TIMETICKS:
            return SmiType.TimeTicks.parse(text);
        case SMIConstants.SYNTAX_OPAQUE:
            return SmiType.Opaque.parse(text);
        case SMIConstants.SYNTAX_COUNTER64 :
            return SmiType.Counter64.parse(text);
        }
        return previousVar.parse(smiSyntax, text);
    }

}
