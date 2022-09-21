package fr.jrds.snmpcodec.smi;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.asn1.BER;
import org.snmp4j.asn1.BERInputStream;
import org.snmp4j.smi.AbstractVariable;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.Utils;
import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.TextualConvention.PatternDisplayHint;

/**
 * A enumeration of Snmp types to help conversion and parsing.
 * @author Fabrice Bacchella
 *
 */
public abstract class SmiType extends Syntax {

    /**
     * <p>From SNMPv2-SMI, defined as [APPLICATION 4]</p>
     * <p> for backward-compatibility only</p>
     * <p>This can also manage the special float type as defined by Net-SNMP. But it don't parse float.</p>
     * @author Fabrice Bacchella
     */
    public static final SmiType Opaque = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Opaque();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof byte[])) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            byte[] a = (byte[]) source;
            return new org.snmp4j.smi.Opaque(a);
        }
        @Override
        public Object convert(Variable v) {
            org.snmp4j.smi.Opaque opaqueVar = (org.snmp4j.smi.Opaque) v;
            //If not resolved, we will return the data as an array of bytes
            Object value = opaqueVar.getValue();
            try {
                byte[] bytesArray = opaqueVar.getValue();
                ByteBuffer bais = ByteBuffer.wrap(bytesArray);
                BERInputStream beris = new BERInputStream(bais);
                byte t1 = bais.get();
                byte t2 = bais.get();
                int l = BER.decodeLength(beris);
                if(t1 == TAG1) {
                    if(t2 == TAG_FLOAT && l == 4)
                        value = bais.getFloat();
                    else if(t2 == TAG_DOUBLE && l == 8)
                        value = bais.getDouble();
                }
            } catch (IOException e) {
                logger.error(opaqueVar.toString());
            }
            return value;
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Opaque(text.getBytes());
        }
        @Override
        public int getSyntaxString() {
            return BER.OPAQUE;
        }
    };

    public static final SmiType OctetString = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.OctetString();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof byte[])) {
                throw new IllegalArgumentException("Given a variable of type " + source.getClass().getName() +" instead of byt[]");
            }
            byte[] a = (byte[]) source;
            return new org.snmp4j.smi.OctetString(a);
        }
        @Override
        public Object convert(Variable v) {
            org.snmp4j.smi.OctetString octetVar = (org.snmp4j.smi.OctetString)v;
            //It might be a C string, try to remove the last 0.
            //But only if the new string is printable
            int length = octetVar.length();
            if(length > 1 && octetVar.get(length - 1) == 0) {
                org.snmp4j.smi.OctetString newVar = octetVar.substring(0, length - 1);
                if(newVar.isPrintable()) {
                    v = newVar;
                    logger.debug("Converting an octet stream from %s to %s", octetVar, v);
                }
            }
            return v.toString();
        }
        @Override
        public String format(Variable v) {
            return v.toString();
        }
        @Override
        public Variable parse(String text) {
            return org.snmp4j.smi.OctetString.fromByteArray(text.getBytes());
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) {
            return new PatternDisplayHint(type, hint, type.getConstrains());
        }
        @Override
        public int getSyntaxString() {
            return BER.OCTETSTRING;
        }
    };

    /**
     * <p>From SNMPv2-SMI, defined as [APPLICATION 2]</p>
     * <p>an unsigned 32-bit quantity</p>
     * <p>indistinguishable from Gauge32</p>
     * @author Fabrice Bacchella
     *
     */
    public static final SmiType Unsigned32 = new SmiType() {
        @Override
        public Variable getVariable() {
            return new UnsignedInteger32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.UnsignedInteger32(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toLong();
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
            return new TextualConvention.Unsigned32DisplayHint<UnsignedInteger32>(type, hint);
        }
        @Override
        public int getSyntaxString() {
            return SMIConstants.SYNTAX_UNSIGNED_INTEGER32;
        }
        @Override
        public String toString() {
            return "Unsigned32";
        }

    };

    /**
     * @deprecated
     *    The BIT STRING type has been temporarily defined in RFC 1442
     *    and obsoleted by RFC 2578. Use OctetString (i.e. BITS syntax)
     *    instead.
     */
    @Deprecated
    public static final SmiType BitString = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.BitString();
        }
        @Override
        public Variable getVariable(Object source) {
            return null;
        }
        @Override
        public Object convert(Variable v) {
            return v.toString();
        }
        @Override
        public int getSyntaxString() {
            return BER.BITSTRING;
        }
    };

    /**
     * From SNMPv-2SMI, defined as [APPLICATION 0]<p>
     * -- (this is a tagged type for historical reasons)
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.IpAddress} variable.</li>
     * <li>{@link #convert(Variable)} return a {@link java.net.InetAddress}.</li>
     * <li>{@link #format(Variable)} try to resolve the hostname associated with the IP address.</li>
     * <li>{@link #parse(String)} parse the string as an hostname or a IP address.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    public static final SmiType IpAddr = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.IpAddress();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof InetAddress) && ! (source instanceof String)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            if (source instanceof InetAddress) {
                return new org.snmp4j.smi.IpAddress((InetAddress) source);
            } else {
                return new org.snmp4j.smi.IpAddress((String) source);
            }
        }
        @Override
        public Object convert(Variable v) {
            return ((IpAddress)v).getInetAddress();
        }
        @Override
        public String format(Variable v) {
            IpAddress ip = (IpAddress) v;
            return ip.getInetAddress().getHostName();
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.IpAddress(text);
        }
        @Override
        public int getSyntaxString() {
            return BER.IPADDRESS;
        }
    };

    public static final SmiType ObjID = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.OID();
        }
        @Override
        public Variable getVariable(Object source) {
            if (source instanceof int[]) {
                int[] oid = (int[]) source;
                return new org.snmp4j.smi.OID(oid);
            } else if(source instanceof String) {
                return new org.snmp4j.smi.OID((String)source);
            } else {
                throw new IllegalArgumentException("Given a variable of type  instead of OID");
            }
        }
        @Override
        public Object convert(Variable v) {
            return v;
        }
        @Override
        public String format(Variable v) {
            return ((OID)v).format();
        }
        @Override
        public Variable parse(String text) {
            return new OID(text);
        }
        @Override
        public int getSyntaxString() {
            return BER.OID;
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) {
            return new TextualConvention.OidTextualConvention(type);
        }
    };

    public static final SmiType INTEGER = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Integer32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  " + source.getClass().getName() + " instead of type Number");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Integer32(n.intValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toInt();
        }
        @Override
        public String format(Variable v) {
            return java.lang.String.valueOf(v.toInt());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Integer32(Integer.parseInt(text));
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
            return new TextualConvention.Signed32DisplayHint<Integer32>(type, hint);
        }
        @Override
        public int getSyntaxString() {
            return BER.INTEGER;
        }
        @Override
        public String toString() {
            return "INTEGER";
        }
    };

    /**
     * <p>From SNMPv2-SMI, defined as [APPLICATION 1]</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.Counter32} variable.</li>
     * <li>{@link #convert(Variable)} return the value stored in a {@link java.lang.Long}.</li>
     * <li>{@link #parse(String)} parse the string as a long value.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    public static final SmiType Counter32 = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Counter32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Counter32(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toLong();
        }
        @Override
        public String format(Variable v) {
            return java.lang.String.valueOf(v.toLong());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Counter32(Long.parseLong(text));
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
            return new TextualConvention.Unsigned32DisplayHint<UnsignedInteger32>(type, hint);
        }
        @Override
        public int getSyntaxString() {
            return BER.COUNTER32;
        }
        @Override
        public String toString() {
            return "Counter32";
        }
    };

    /**
     * From SNMPv2-SMI, defined as [APPLICATION 6]<p>
     * -- for counters that wrap in less than one hour with only 32 bits</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.Counter64} variable.</li>
     * <li>{@link #convert(Variable)} return the value stored in a {@link fr.jrds.snmpcodec.Utils.UnsignedLong}.</li>
     * <li>{@link #parse(String)} parse the string as a long value.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    public static final SmiType Counter64 = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Counter64();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Counter64(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return Utils.getUnsigned(v.toLong());
        }
        @Override
        public String format(Variable v) {
            return Long.toUnsignedString(v.toLong());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Counter64(Long.parseLong(text));
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
            return new TextualConvention.Counter64DisplayHint(type, hint);
        }
        @Override
        public int getSyntaxString() {
            return BER.COUNTER64;
        }
    };

    /**
     * From SNMPv-2SMI, defined as [APPLICATION 2]<p>
     * -- this doesn't wrap</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.Gauge32} variable.</li>
     * <li>{@link #convert(Variable)} return the value stored in a Long.</li>
     * <li>{@link #parse(String)} parse the string as a long value.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    public static final SmiType Gauge32 = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Gauge32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Gauge32(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toLong();
        }
        @Override
        public String format(Variable v) {
            return java.lang.String.valueOf(v.toLong());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Gauge32(Long.parseLong(text));
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
            return new TextualConvention.Unsigned32DisplayHint<org.snmp4j.smi.Gauge32>(type, hint);
        }
        @Override
        public int getSyntaxString() {
            return BER.COUNTER32;
        }
        @Override
        public String toString() {
            return "Gauge32";
        }
    };

    /**
     * From SNMPv-2SMI, defined as [APPLICATION 3]<p>
     * -- hundredths of seconds since an epoch</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.TimeTicks} variable.</li>
     * <li>{@link #convert(Variable)} return the time ticks as a number of milliseconds stored in a Long</li>
     * <li>{@link #format(Variable)} format the value using {@link org.snmp4j.smi.TimeTicks#toString()}
     * <li>{@link #parse(String)} can parse a number, expressing timeticks or the result of {@link org.snmp4j.smi.TimeTicks#toString()}
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    public static final SmiType TimeTicks = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.TimeTicks();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new TimeTicks(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return ((TimeTicks)v).toMilliseconds();
        }
        @Override
        public String format(Variable v) {
            return v.toString();
        }
        @Override
        public Variable parse(String text) {
            try {
                long duration = Long.parseLong(text);
                return new org.snmp4j.smi.TimeTicks(duration);
            } catch (NumberFormatException e) {
                Matcher m = TimeTicksPattern.matcher(text);
                if (m.matches()) {
                    String days = m.group("days") != null ? m.group("days") : "0";
                    String hours = m.group("hours");
                    String minutes = m.group("minutes");
                    String seconds = m.group("seconds");
                    String fraction = m.group("fraction");
                    String formatted = java.lang.String.format("P%sDT%sH%sM%s.%sS", days, hours, minutes,seconds, fraction);
                    TimeTicks tt = new TimeTicks();
                    tt.fromMilliseconds(Duration.parse(formatted).toMillis());
                    return tt;
                } else {
                    return new org.snmp4j.smi.Null();
                }
            }
        }
        @Override
        public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
            return new TextualConvention.Unsigned32DisplayHint<org.snmp4j.smi.Gauge32>(type, hint);
        }
        @Override
        public int getSyntaxString() {
            return BER.TIMETICKS;
        }
    };

    public static final SmiType Null = new SmiType() {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Null();
        }
        @Override
        public Variable getVariable(Object source) {
            return getVariable();
        }
        @Override
        public Object convert(Variable v) {
            return null;
        }
        @Override
        public String format(Variable v) {
            return "";
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Null();
        }
        @Override
        public int getSyntaxString() {
            return BER.NULL;
        }
    };

    // Used to parse time ticks
    private static final Pattern TimeTicksPattern = Pattern.compile("(?:(?<days>\\d+) days?, )?(?<hours>\\d+):(?<minutes>\\d+):(?<seconds>\\d+)(?:\\.(?<fraction>\\d+))?");

    private static final LogAdapter logger = LogAdapter.getLogger(SmiType.class);

    private static final byte TAG1 = (byte) 0x9f;
    private static final byte TAG_FLOAT = (byte) 0x78;
    private static final byte TAG_DOUBLE = (byte) 0x79;

    protected SmiType() {
        super(null, null);
    }

    public String format(Variable v) {
        return v.toString();
    }

    public Variable parse(String text) {
        return null;
    }

    public abstract Object convert(Variable v);
    public abstract int getSyntaxString();
    public Object make(int[] in){
        Variable v = getVariable();
        OID oid = new OID(in);
        v.fromSubIndex(oid, true);
        return convert(v);
    }
    @Override
    public Constraint getConstrains() {
        return null;
    }
    @Override
    public String toString() {
        return AbstractVariable.getSyntaxString(getSyntaxString());
    }

    @Override
    public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
        return null;
    }

}
