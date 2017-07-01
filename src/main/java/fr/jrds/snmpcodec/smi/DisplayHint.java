package fr.jrds.snmpcodec.smi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;

public abstract class DisplayHint {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Name {
        String value();
    }

    @Name("DateAndTime")
    public static class DateAndTime extends DisplayHint {
        private final Pattern HINT = Pattern.compile("(\\d+)-(\\d+)-(\\d+),(\\d+):(\\d+):(\\d+).(\\d+),(\\+|-)(\\d+):(\\d+)");

        @Override
        public String format(Variable v) {
            OctetString os = (org.snmp4j.smi.OctetString) v;
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
            Matcher match = HINT.matcher(text);
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
    };

    @Name("StorageType")
    public static class StorageType extends DisplayHint {

        @Override
        public String format(Variable v) {
            Integer32 st = (Integer32) v;
            switch(st.getValue()) {
            case 1:
                return "other";
            case 2:
                return "volatile";
            case 3:
                return "nonVolatile";
            case 4:
                return "permanent";
            case 5:
                return "readOnly";
            default:
                return null;
            }
        }

        @Override
        public Variable parse(String text) {
            int val = -1;
            switch(text.toLowerCase()) {
            case "other":
                val = 1; break;
            case "volatile":
                val = 2; break;
            case "nonvolatile":
                val = 3; break;
            case "permanent":
                val = 4; break;
            case "readonly":
                val = 5; break;
            }
            if (val > 0) {
                return new Integer32(val);
            } else {
                return null;
            }
        }
    };

    private static class PatternDisplayHint extends DisplayHint {
        private static final Pattern element = Pattern.compile("(.*?)(\\*?)(\\d*)([dxatobh])([^\\d\\*-]?)(-\\d+)?");
        private static final Charset ASCII = Charset.forName("US-ASCII");
        private static final Charset UTF8 = Charset.forName("UTF-8");
        private final String hint;
        String[] paddings;
        Character[] stars;
        Integer[] sizes;
        Character[] formats;
        Character[] separators;
        Integer[] decimals;

        PatternDisplayHint(String hint) {
            super();
            this.hint = hint;
            Matcher m = element.matcher(hint);
            List<String> paddings = new ArrayList<>();
            List<Character> stars = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            List<Character> formats = new ArrayList<>();
            List<Character> separators = new ArrayList<>();
            List<Integer> decimals = new ArrayList<>();
            int end = -1;
            while (m.find()){
                paddings.add(m.group(1));
                if ( !m.group(2).isEmpty()) {
                    stars.add(m.group(2).charAt(0));
                }
                if (! m.group(3).isEmpty()) {
                    sizes.add(Integer.parseInt(m.group(3)));
                } else {
                    sizes.add(1);
                }
                formats.add(m.group(4).charAt(0));
                if ( !m.group(5).isEmpty()) {
                    separators.add(m.group(5).charAt(0));
                } else {
                    separators.add(Character.MIN_VALUE);
                }
                if (m.group(6) != null) {
                    decimals.add(Integer.parseInt(m.group(6).substring(1)));
                } else {
                    decimals.add(0);
                }
                end = m.end();
            }
            if (end >= 0) {
                paddings.add(hint.substring(end));
            }
            this.paddings = paddings.toArray(new String[paddings.size()]);
            this.stars = stars.toArray(new Character[stars.size()]);
            this.sizes = sizes.toArray(new Integer[sizes.size()]);
            this.formats = formats.toArray(new Character[formats.size()]);
            this.separators = separators.toArray(new Character[separators.size()]);
            this.decimals = decimals.toArray(new Integer[decimals.size()]);
        }

        @Override
        public String format(Variable v) {
            OctetString os = (org.snmp4j.smi.OctetString) v;
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

        @Override
        public Variable parse(String text) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    public abstract String format(Variable v);
    public abstract Variable parse(String text);

    public static void addAnnotation(Class<? extends DisplayHint> clazz, Map<Symbol, DisplayHint> annotations) {
        Name annotation = clazz.getAnnotation(Name.class);
        if (annotation != null) {
            try {
                annotations.put(new Symbol(annotation.value()), clazz.newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Missing name annotation for TextualConvention");
        }
    }

    public static void addAnnotation(Symbol name, String displayHint, Map<Symbol, DisplayHint> annotations) {
        annotations.put(name, new PatternDisplayHint(displayHint));
    }

}
