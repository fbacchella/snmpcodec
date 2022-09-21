package fr.jrds.snmpcodec.log;

import java.io.Serializable;
import java.util.Arrays;

class LogString implements CharSequence, Serializable {

    public static LogString make(String format, Object...objects) {
        return new LogString(format, objects);
    }

    private static final long serialVersionUID = 1806912693104264455L;

    private String formatted = null;
    private final String format;
    private final Object[] objects;

    private LogString(String format, Object... objects) {
        this.format = format;
        this.objects = objects;
    }

    private synchronized String format() {
        if (formatted == null) {
            Object[] mapped = Arrays.stream(objects)
                                      .map(i -> i instanceof Object[] ? Arrays.toString((Object[])i):i)
                                      .map(i -> i instanceof byte[] ? Arrays.toString((byte[])i):i)
                                      .map(i -> i instanceof short[] ? Arrays.toString((short[])i):i)
                                      .map(i -> i instanceof int[] ? Arrays.toString((int[])i):i)
                                      .map(i -> i instanceof long[] ? Arrays.toString((long[])i):i)
                                      .map(i -> i instanceof float[] ? Arrays.toString((float[])i):i)
                                      .map(i -> i instanceof double[] ? Arrays.toString((double[])i):i)
                                      .map(i -> i instanceof char[] ? Arrays.toString((char[])i):i)
                                      .map(i -> i instanceof boolean[] ? Arrays.toString((boolean[])i):i)
                                      .toArray();
            formatted = String.format(format, mapped);
        }
        return formatted;
    }

    @Override
    public int length() {
        return format().length();
    }

    @Override
    public char charAt(int index) {
        return format().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return format().substring(start, end);
    }

    @Override
    public String toString() {
        return format();
    }

}
