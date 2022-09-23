package fr.jrds.snmpcodec.smi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.smi.AssignableFromString;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.smi.Constraint.ConstraintElement;

public abstract class TextualConvention extends AnnotedSyntax implements SyntaxContainer {

    public static class OidTextualConvention extends TextualConvention {

        public OidTextualConvention(Syntax syntax) {
            super(syntax, null, null);
        }

    }

    public abstract static class AbstractPatternDisplayHint<V extends Variable> extends TextualConvention {

        protected final String hint;

        protected AbstractPatternDisplayHint(Syntax syntax, String hint, Map<Number, String> names, Constraint constraints) {
            super(syntax, names, constraints);
            this.hint = hint;
        }
        public String getHint() {
            return hint;
        }
        @Override
        public Variable parse(String text) {
            if (hint == null) {
                return super.parse(text);
            } else {
                return patternParse(text);
            }
        }
        @SuppressWarnings("unchecked")
        @Override
        public final String format(Variable v) {
            if (hint == null) {
                return super.format(v);
            } else {
                return patternFormat((V) v);
            }
        }
        protected abstract String patternFormat(V v);
        protected abstract Variable patternParse(String text);
    }

    public static class DateAndTime extends AbstractPatternDisplayHint<OctetString> {
        private static final Constraint Constraint8or11 = new Constraint(true);
        static {
            Constraint8or11.add(new ConstraintElement(8));
            Constraint8or11.add(new ConstraintElement(11));
        }

        private static final AnnotedSyntax localsyntax = new AnnotedSyntax(SmiType.OctetString, null, Constraint8or11);

        private static final Pattern HINTREGEX = Pattern.compile("(\\d+)-(\\d+)-(\\d+),(\\d+):(\\d+):(\\d+).(\\d+)(,([+-])(\\d+):(\\d+))?");

        public DateAndTime() {
            super(localsyntax, "2d-1d-1d,1d:1d:1d.1d,1a1d:1d", null, Constraint8or11);
        }

        @Override
        public String patternFormat(OctetString os) {
            ByteBuffer buffer = ByteBuffer.wrap(os.toByteArray());
            buffer.order(ByteOrder.BIG_ENDIAN);
            int year = buffer.getShort();
            int month = buffer.get();
            int day = buffer.get();
            int hour = buffer.get();
            int minutes = buffer.get();
            int seconds = buffer.get();
            int deciSeconds = buffer.get();
            String zoneOffset;
            if (buffer.hasRemaining()) {
                char directionFromUTC = Character.toChars(buffer.get())[0];
                int hourFromUTC = buffer.get();
                int minutesFromUTC = buffer.get();
                zoneOffset = String.format(",%c%d:%d", directionFromUTC, hourFromUTC, minutesFromUTC);
            } else {
                zoneOffset = "";
            }

            return String.format("%d-%d-%d,%d:%d:%d.%d%s", year, month, day, hour, minutes, seconds, deciSeconds, zoneOffset);
        }

        @Override
        public Variable patternParse(String text) {
            Matcher match = HINTREGEX.matcher(text);
            if (!match.find()) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.allocate(match.group(8) != null ? 11: 8);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putShort(Short.parseShort(match.group(1))); // year
            buffer.put(Byte.parseByte(match.group(2)));        // month
            buffer.put(Byte.parseByte(match.group(3)));        // day
            buffer.put(Byte.parseByte(match.group(4)));        // hour
            buffer.put(Byte.parseByte(match.group(5)));        // minutes
            buffer.put(Byte.parseByte(match.group(6)));        // seconds
            buffer.put(Byte.parseByte(match.group(7)));        // deci-seconds
            if (match.group(8) != null) {
                buffer.put(match.group(9).getBytes()[0]);          // direction from UTC
                buffer.put(Byte.parseByte(match.group(10)));        // hours from UTC*
                buffer.put(Byte.parseByte(match.group(11)));       // hours from UTC*
            }
            return OctetString.fromByteArray(buffer.array());
        }

    }

    private abstract static class NumberDisplayHint<V extends Variable> extends AbstractPatternDisplayHint<V> {
        private static final Pattern floatPattern = Pattern.compile("(?<length>\\d+)?(?<radix>[dxob])(?:-(?<float>\\d+))?");

        protected final int fixedfloat;
        protected final char radix;

        protected NumberDisplayHint(Syntax syntax, String hint) throws MibException {
            super(syntax, hint, null, null);
            if (hint != null) {
                Matcher m = floatPattern.matcher(hint);
                if (m.matches()) {
                    radix = m.group("radix").charAt(0);
                    String floatSuffix = m.group("float");
                    if (floatSuffix == null) {
                        fixedfloat = 0;
                    } else {
                        fixedfloat = Integer.parseInt(floatSuffix);
                    }
                } else {
                    throw new MibException("Invalid display hint '" + hint + "'");
                }
            } else {
                fixedfloat = -1;
                radix = Character.MIN_VALUE;
            }
        }

        @Override
        public Variable patternParse(String text) {
            Variable val = getSyntax().getVariable();
            ((AssignableFromString) val).setValue(text);
            return val;
        }

        protected String patternFormat(V val) {
            long l = val.toLong();
            if (fixedfloat == 0) {
                switch(radix) {
                case 'd':
                    return Long.toString(l);
                case 'x':
                    return Long.toHexString(l);
                case 'o':
                    return Long.toOctalString(l);
                case 'b':
                    return Long.toBinaryString(l);
                default:
                    // not reachable
                }
            } else {
                char[] formatted = Long.toString(l).toCharArray();
                if (formatted.length > fixedfloat) {
                    char[] newformatted = new char[formatted.length + 1];
                    int limit = formatted.length - fixedfloat;
                    System.arraycopy(formatted, 0, newformatted, 0, limit);
                    newformatted[limit] = '.';
                    System.arraycopy(formatted, limit, newformatted, limit + 1, fixedfloat);
                    return new String(newformatted);
                } else {
                    char[] newformatted = new char[fixedfloat + 1];
                    Arrays.fill(newformatted, '0');
                    newformatted[0] = '.';
                    for (int i = formatted.length - 1 ; i >= 0 ; i--) {
                        newformatted[fixedfloat - formatted.length + i + 1] = formatted[i];
                    }
                    return new String(newformatted);
                }
            }
            return null;
        }

    }

    public static class Unsigned32DisplayHint<V extends UnsignedInteger32> extends NumberDisplayHint<V> {

        protected Unsigned32DisplayHint(Syntax syntax, String hint) throws MibException {
            super(syntax, hint);
        }

    }

    public static class Signed32DisplayHint<V extends Integer32> extends NumberDisplayHint<V> {
        protected Signed32DisplayHint(Syntax syntax, String hint) throws MibException {
            super(syntax, hint);
        }

    }

    public static class Counter64DisplayHint extends NumberDisplayHint<Counter64> {

        protected Counter64DisplayHint(Syntax syntax, String hint) throws MibException {
            super(syntax, hint);
        }

    }

    private static class DisplayHintClause {
        private final boolean repeat;
        private final int length;
        private final char format;
        private final char separator;
        private final char terminator;

        public DisplayHintClause(boolean repeat, int length, char format, char separator, char terminator) {
            this.repeat = repeat;
            this.length = length;
            this.format = format;
            this.separator = separator;
            this.terminator = terminator;
        }

        @Override
        public String toString() {
            return "DisplayHintClause{" + "repeat=" + repeat + ", length=" + length + ", format=" + format + ", separator=" + separator + ", terminator=" + terminator + '}';
        }
    }

    public static class PatternDisplayHint extends AbstractPatternDisplayHint<OctetString> {
        private static final Pattern element = Pattern.compile("(?<repeat>\\*)?(?<length>\\d*)(?<format>[xdoath])(?<separator>[^\\d\\*](?<terminator>[^\\d\\*])?)?");
        private final DisplayHintClause[] clauses;

        public PatternDisplayHint(Syntax syntax, String hint, Constraint constraint) throws MibException {
            super(syntax, hint, null, constraint);
            if (hint != null) {
                boolean repeat;
                int length;
                char format;
                char separator;
                char terminator;

                Matcher m = element.matcher(hint);
                List<DisplayHintClause> currentClauses = new ArrayList<>();
                while (m.find()) {
                    if (m.group("repeat") != null) {
                        repeat = true;
                    } else {
                        repeat = false;
                    }
                    if (!m.group("length").isEmpty()) {
                        length = Integer.parseInt(m.group("length"));
                    } else {
                        length = 0;
                    }
                    format = m.group("format").charAt(0);
                    if (m.group("separator") != null) {
                        separator = m.group("separator").charAt(0);
                    } else {
                        separator = Character.MIN_VALUE;
                    }
                    if (m.group("terminator") != null && repeat) {
                        terminator = m.group("terminator").charAt(0);
                    } else {
                        terminator= Character.MIN_VALUE;
                    }
                    currentClauses.add(new DisplayHintClause(repeat, length, format, separator, terminator));
                }
                if (currentClauses.isEmpty()) {
                    throw new MibException("Invalid display hint " + hint + " ");
                }
                this.clauses = currentClauses.stream().toArray(DisplayHintClause[]::new);
            } else {
                this.clauses = new DisplayHintClause[0];
            }
        }

        @Override
        public String patternFormat(OctetString os) {
            if (hint == null) {
                return SmiType.OctetString.format(os);
            } else {
                ByteBuffer buffer = ByteBuffer.wrap(os.toByteArray());
                buffer.order(ByteOrder.BIG_ENDIAN);
                StringBuilder formatted = new StringBuilder();
                for (DisplayHintClause clause: clauses) {
                    if (! buffer.hasRemaining()) {
                        break;
                    }
                    int repeat = clause.repeat ? buffer.get() : 1;
                    // Some modules forget the length, consume everything in one shot then
                    int length = clause.length != 0 ? clause.length : buffer.remaining();
                    for (int i = 0; i < repeat ; i++) {
                        switch (clause.format) {
                        case 'd':
                        case 'x':
                        case 'o':
                            formatted.append(resolveNumerical("%" + clause.format, buffer, length));
                            break;
                        case 'a':
                        case 't':
                            byte[] sub = new byte[Math.min(clause.length, buffer.remaining())];
                            buffer.get(sub);
                            formatted.append(new String(sub, clause.format == 'a' ? StandardCharsets.US_ASCII : StandardCharsets.UTF_8));
                            break;
                        case 'h':
                        default:
                            //unreachable
                        }
                        if (clause.separator != Character.MIN_VALUE && buffer.hasRemaining()) {
                            formatted.append(clause.separator);
                        }
                    }
                }
                return formatted.toString();
            }
        }

        private String resolveNumerical(String numberFormatter, ByteBuffer buffer, int length) {
            switch (length) {
            case 1:
                return String.format(numberFormatter, buffer.get());
            case 2:
                return String.format(numberFormatter, buffer.getShort());
            case 4:
                return String.format(numberFormatter, buffer.getInt());
            default:
                throw new IllegalArgumentException("Invalid length " + length);
            }
        }

        @Override
        public Variable patternParse(String text) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public String toString() {
            return "DisplayHint[" + getHint() + "]";
        }

    }

    public static class DisplayString extends AbstractPatternDisplayHint<OctetString> {
        private static final Constraint Constraint255a = new Constraint(true);
        static {
            Constraint255a.add(new ConstraintElement(255));
        }

        private static final AnnotedSyntax localsyntax = new AnnotedSyntax(SmiType.OctetString, null, Constraint255a);

        public DisplayString() {
            super(localsyntax, "255a", null, null);
        }

        @Override
        public String patternFormat(OctetString v) {
            if (v.isPrintable()) {
                return new String(v.getValue(), StandardCharsets.US_ASCII);
            } else {
                return v.toHexString();
            }
        }

        @Override
        public Variable patternParse(String text) {
            return new OctetString(text.getBytes(StandardCharsets.US_ASCII));
        }

        @Override
        public Object convert(Variable v) {
            return patternFormat((OctetString) v);
        }

    }

    public static class Bits  extends TextualConvention {
        Bits() {
            super(null, null, null);
        }
    }

    private TextualConvention(Syntax syntax, Map<Number, String> names, Constraint constraints) {
        super(syntax, names, constraints);
    }

    @Override
    public String toString() {
        return String.format("TextualConvention[%s]", getSyntax());
    }

}
