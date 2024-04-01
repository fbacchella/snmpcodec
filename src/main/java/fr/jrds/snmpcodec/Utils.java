package fr.jrds.snmpcodec;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Utils {

    // Private constructor, not instance ever used
    private Utils() {

    }

    public static String dottedNotation(int[] elements){
        // SimpleOIDTextFormat does masking too
        return Arrays.stream(elements).mapToObj(i -> Long.toString(i & 0xFFFFFFFFL)).collect(Collectors.joining("."));
    }

    public static final class UnsignedLong extends Number implements Comparable<Long>{
        private final long l;

        private UnsignedLong(long l) {
            super();
            this.l = l;
        }

        @Override
        public byte byteValue() {
            return (byte) l;
        }

        @Override
        public short shortValue() {
            return (short) l;
        }

        public int intValue() {
            return (int) l;
        }

        public long longValue() {
            return l;
        }

        public float floatValue() {
            return l;
        }

        public double doubleValue() {
            return l;
        }

        public String toString() {
            return Long.toUnsignedString(l);
        }

        public int hashCode() {
            return Long.hashCode(l);
        }

        public boolean equals(Object obj) {
            if (obj instanceof Long) {
                return l == (Long) obj;
            }
            return false;
        }

        public int compareTo(Long l2) {
            return Long.compareUnsigned(l, l2);
        }

    }

    public static Utils.UnsignedLong getUnsigned(long l) {
        return new Utils.UnsignedLong(l);
    }

}
