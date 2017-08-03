package fr.jrds.snmpcodec.smi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.smi.Constraint.ConstraintElement;

public abstract class TextualConvention extends IndirectSyntax {
    
    public static class OidTextualConvention extends TextualConvention {

        public OidTextualConvention(Syntax syntax) {
            super(syntax, null, null);
        }

        @Override
        public String format(Variable v) {
            return SmiType.ObjID.format(v);
        }

        @Override
        public Object convert(Variable v) {
            return SmiType.ObjID.convert(v);
        }

        @Override
        public Variable parse(String text) {
            return SmiType.ObjID.parse(text);
        }

        @Override
        public Variable getVariable() {
            return SmiType.ObjID.getVariable();
        }

        @Override
        public Variable getVariable(Object source) {
            return SmiType.ObjID.getVariable(source);
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
        @SuppressWarnings("unchecked")
        @Override
        public final String format(Variable v) {
            return patternformat((V) v);
        }
        protected abstract String patternformat(V v);
    }

    public static class DateAndTime extends AbstractPatternDisplayHint<OctetString> {
        private final static Constraint Constraint8or11 = new Constraint(true);
        static {
            Constraint8or11.add(new ConstraintElement(8));
            Constraint8or11.add(new ConstraintElement(11));
        }
        private final static IndirectSyntax localsyntax = new IndirectSyntax(SmiType.OctetString, null, Constraint8or11);
        static private final Charset USASCII = Charset.forName("US-ASCII");

        private static final Pattern HINTREGEX = Pattern.compile("(\\d+)-(\\d+)-(\\d+),(\\d+):(\\d+):(\\d+).(\\d+),(\\+|-)(\\d+):(\\d+)");

        public DateAndTime() {
            super(localsyntax, "2d-1d-1d,1d:1d:1d.1d,1a1d:1d", null, null);
        }

        @Override
        public String patternformat(OctetString os) {
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
        public Variable parse(String text) {
            Matcher match = HINTREGEX.matcher(text);
            if (!match.find()) {
                return null;
            };
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

        @Override
        public Constraint getConstrains() {
            return null;
        }

        @Override
        public Object convert(Variable v) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Variable getVariable() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Variable getVariable(Object source) {
            // TODO Auto-generated method stub
            return null;
        }

    };

    private static final Pattern floatPattern = Pattern.compile("(?<radix>d|x|o|b)(?:-(?<float>\\d+))?");
    private static abstract class NumberDisplayHint<V extends Variable> extends AbstractPatternDisplayHint<V> {
        protected final int fixedfloat;
        protected final char radix;
        protected NumberDisplayHint(Syntax syntax, String hint) {
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
                    throw new RuntimeException("Invalid display hint " + hint);
                }
            } else {
                fixedfloat = 0;
                radix = ' ';
            }
        }
        @Override
        public Constraint getConstrains() {
            return null;
        }
        protected abstract void setVal(V var, String text);
        @Override
        public Variable parse(String text) {
            V val = (V) getSyntax().getVariable();
            setVal(val, text);
            return val;
        }

        protected String patternformat(long l) {
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

        @Override
        public Variable getVariable() {
            return getSyntax().getVariable();
        }
    }

    public static class Unsigned32DisplayHint<V extends UnsignedInteger32> extends NumberDisplayHint<V> {

        protected Unsigned32DisplayHint(Syntax syntax, String hint) {
            super(syntax, hint);
        }

        @Override
        protected String patternformat(V v) {
            return patternformat(v.getValue());
        }

        @Override
        protected void setVal(UnsignedInteger32 var, String text) {
            var.setValue(text);
        }

        @Override
        public Object convert(Variable v) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Variable getVariable(Object source) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public static class Signed32DisplayHint<V extends Integer32> extends NumberDisplayHint<V> {
        protected Signed32DisplayHint(Syntax syntax, String hint) {
            super(syntax, hint);
        }

        @Override
        public Constraint getConstrains() {
            return null;
        }

        @Override
        protected String patternformat(Integer32 v) {
            return patternformat(v.getValue());
        }

        @Override
        protected void setVal(Integer32 var, String text) {
            var.setValue(text);
        }

        @Override
        public Object convert(Variable v) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Variable getVariable(Object source) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public static class Counter64DisplayHint extends NumberDisplayHint<Counter64> {

        protected Counter64DisplayHint(Syntax syntax, String hint) {
            super(syntax, hint);
        }

        @Override
        protected void setVal(Counter64 var, String text) {
            var.setValue(text);
        }

        @Override
        protected String patternformat(Counter64 v) {
            return patternformat(v.getValue());
        }

        @Override
        public Variable getVariable(Object source) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object convert(Variable v) {
            // TODO Auto-generated method stub
            return null;
        }
    }


    public static class PatternDisplayHint extends AbstractPatternDisplayHint<OctetString> {
        private static final Pattern element = Pattern.compile("(.*?)(\\*?)(\\d*)([dxatobh])([^\\d\\*-]?)(-\\d+)?");
        private static final Charset ASCII = Charset.forName("US-ASCII");
        private static final Charset UTF8 = Charset.forName("UTF-8");
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
        public String patternformat(OctetString os) {
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
                        formatted.append(new String(sub, formats[i] == 'a' ? ASCII : UTF8));
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
        public Variable parse(String text) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public String toString() {
            return "DisplayHint[" + getHint() + "]";
        }

        @Override
        public Constraint getConstrains() {
            return constraint;
        }

        @Override
        public Variable getVariable() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object convert(Variable v) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Variable getVariable(Object source) {
            // TODO Auto-generated method stub
            return null;
        }
    }


    public static class DisplayString extends AbstractPatternDisplayHint<OctetString> {
        private final static Constraint Constraint255a = new Constraint(true);
        static {
            Constraint255a.add(new ConstraintElement(255));
        }
        private final static IndirectSyntax localsyntax = new IndirectSyntax(SmiType.OctetString, null, Constraint255a);
        static private final Charset USASCII = Charset.forName("US-ASCII");

        public DisplayString() {
            super(localsyntax, "255a", null, null);
        }

        @Override
        public String patternformat(OctetString v) {
            if (v.isPrintable()) {
                return new String(v.getValue(), USASCII);
            } else {
                return v.toHexString();
            }
        }

        @Override
        public Variable parse(String text) {
            return new OctetString(text.getBytes(USASCII));
        }

        @Override
        public Variable getVariable() {
            return SmiType.OctetString.getVariable();
        }

        @Override
        public Object convert(Variable v) {
            return patternformat((OctetString) v);
        }

        @Override
        public Variable getVariable(Object source) {
            // TODO Auto-generated method stub
            return null;
        }

    }

    public TextualConvention(Syntax syntax, Map<Number, String> names, Constraint constraints) {
        super(syntax, names, constraints);
    }

}
