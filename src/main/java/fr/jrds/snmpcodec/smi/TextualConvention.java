package fr.jrds.snmpcodec.smi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
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

    public static abstract class AbstractPatternDisplayHint<V extends Variable> extends TextualConvention {

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
        private final static Constraint Constraint8or11 = new Constraint(true);
        static {
            Constraint8or11.add(new ConstraintElement(8));
            Constraint8or11.add(new ConstraintElement(11));
        }
        private final static AnnotedSyntax localsyntax = new AnnotedSyntax(SmiType.OctetString, null, Constraint8or11);

        private static final Pattern HINTREGEX = Pattern.compile("(\\d+)-(\\d+)-(\\d+),(\\d+):(\\d+):(\\d+).(\\d+),(\\+|-)(\\d+):(\\d+)");

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
            int deciseconds = buffer.get();
            char directionFromUTC = Character.toChars(buffer.get())[0];
            int hourFromUTC = buffer.get();
            int minutesFromUTC = buffer.get();

            return String.format("%d-%d-%d,%d:%d:%d.%d,%c%d:%d", year, month, day, hour, minutes, seconds, deciseconds, directionFromUTC, hourFromUTC, minutesFromUTC);
        }

        @Override
        public Variable patternParse(String text) {
            Matcher match = HINTREGEX.matcher(text);
            if (!match.find()) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.allocate(11);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putShort(Short.parseShort(match.group(1))); // year
            buffer.put(Byte.parseByte(match.group(2)));        // month
            buffer.put(Byte.parseByte(match.group(3)));        // day
            buffer.put(Byte.parseByte(match.group(4)));        // hour
            buffer.put(Byte.parseByte(match.group(5)));        // minutes
            buffer.put(Byte.parseByte(match.group(6)));        // seconds
            buffer.put(Byte.parseByte(match.group(7)));        // deci-seconds
            buffer.put(match.group(8).getBytes()[0]);          // direction from UTC
            buffer.put(Byte.parseByte(match.group(9)));        // hours from UTC*
            buffer.put(Byte.parseByte(match.group(10)));       // hours from UTC*
            return OctetString.fromByteArray(buffer.array());
        }

    }

    private static abstract class NumberDisplayHint<V extends Variable> extends AbstractPatternDisplayHint<V> {
        private static final Pattern floatPattern = Pattern.compile("(?<radix>d|x|o|b)(?:-(?<float>\\d+))?");

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
                    throw new MibException("Invalid display hint " + hint);
                }
            } else {
                fixedfloat = -1;
                radix = '\0';
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

    public static class PatternDisplayHint extends AbstractPatternDisplayHint<OctetString> {
        private static final Pattern element = Pattern.compile("(.*?)(\\*?)(\\d*)([dxatobh])([^\\d\\*-]?)(-\\d+)?");
        private final String[] paddings;
        private final Character[] stars;
        private final Integer[] sizes;
        private final Character[] formats;
        private final Character[] separators;
        private final Integer[] decimals;
        private final Constraint constraint;

        public PatternDisplayHint(Syntax syntax, String hint, Constraint constraint) {
            super(syntax, hint, null, constraint);
            this.constraint = constraint;
            if (hint != null) {
                Matcher m = element.matcher(hint);
                List<String> paddings = new ArrayList<>();
                List<Character> stars = new ArrayList<>();
                List<Integer> sizes = new ArrayList<>();
                List<Character> formats = new ArrayList<>();
                List<Character> separators = new ArrayList<>();
                List<Integer> decimals = new ArrayList<>();
                int end = -1;
                while (m.find()) {
                    paddings.add(m.group(1));
                    if(!m.group(2).isEmpty()) {
                        stars.add(m.group(2).charAt(0));
                    }
                    if(!m.group(3).isEmpty()) {
                        sizes.add(Integer.parseInt(m.group(3)));
                    } else {
                        sizes.add(1);
                    }
                    formats.add(m.group(4).charAt(0));
                    if(!m.group(5).isEmpty()) {
                        separators.add(m.group(5).charAt(0));
                    } else {
                        separators.add(Character.MIN_VALUE);
                    }
                    if(m.group(6) != null) {
                        decimals.add(Integer.parseInt(m.group(6).substring(1)));
                    } else {
                        decimals.add(0);
                    }
                    end = m.end();
                }
                if(end >= 0) {
                    paddings.add(hint.substring(end));
                }
                this.paddings = paddings.toArray(new String[paddings.size()]);
                this.stars = stars.toArray(new Character[stars.size()]);
                this.sizes = sizes.toArray(new Integer[sizes.size()]);
                this.formats = formats.toArray(new Character[formats.size()]);
                this.separators = separators.toArray(new Character[separators.size()]);
                this.decimals = decimals.toArray(new Integer[decimals.size()]);
            } else {
                this.paddings = null;
                this.stars = null;
                this.sizes = null;
                this.formats = null;
                this.separators = null;
                this.decimals = null;
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
                for (int i = 0 ; i < sizes.length; i++) {
                    formatted.append(paddings[i]);
                    int size = sizes[i];
                    switch (formats[i]) {
                    case 'd':
                        switch (size) {
                        case 1:
                            formatted.append(buffer.get());
                            break;
                        case 2:
                            formatted.append(buffer.getShort());
                            break;
                        case 4:
                            formatted.append(buffer.getInt());
                            break;
                        }
                        break;
                    case 'x':
                        switch (size) {
                        case 1:
                            formatted.append(String.format("%x", buffer.get()));
                            break;
                        case 2:
                            formatted.append(String.format("%x", buffer.getShort()));
                            break;
                        case 4:
                            formatted.append(String.format("%x", buffer.getInt()));
                            break;
                        }
                        break;
                    case 'a':
                    case 't':
                        byte[] sub =new byte[sizes[i]];
                        buffer.get(sub);
                        formatted.append(new String(sub, formats[i] == 'a' ? StandardCharsets.US_ASCII : StandardCharsets.UTF_8));
                        break;
                    case 'h':
                    }
                    if (separators[i] != Character.MIN_VALUE) {
                        formatted.append(separators[i]);
                    }
                }
                formatted.append(paddings[paddings.length - 1]);
                return formatted.toString();
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
        private final static Constraint Constraint255a = new Constraint(true);
        static {
            Constraint255a.add(new ConstraintElement(255));
        }
        private final static AnnotedSyntax localsyntax = new AnnotedSyntax(SmiType.OctetString, null, Constraint255a);

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
