package fr.jrds.snmpcodec.smi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fr.jrds.snmpcodec.smi.Index.Parsed;

public class Constraint {

    public static class ConstraintElement {
        public final Object value;
        public final Object from;
        public final Object to;

        public ConstraintElement(Object value) {
            this.value = value;
            this.from = null;
            this.to = null;
        }
        public ConstraintElement(Object from, Object to) {
            this.value = null;
            this.from = from;
            this.to = to;
        }
        @Override
        public String toString() {
            if (value != null) {
                return value.toString();
            } else {
                return from.toString() + ".." + to.toString();
            }
        }
    }
    
    public static class Builder {
        private final Type type;
        private final List<ConstraintElement> ranges = new ArrayList<>();

        private Builder(Type type) {
            this.type = type;
        }

        public Builder add(ConstraintElement newElement) {
            this.ranges.add(newElement);
            return this;
        }

        public Constraint build() {
            Constraint c = new Constraint(type);
            c.ranges.addAll(ranges);
            if (ranges.size() > 1) {
                c.variableSize = true;
            } else if (ranges.size() == 1 && ranges.get(0).value == null) {
                c.variableSize = true;
            } else {
                c.variableSize = false;
            }
            return c;
        }
    }

    public static Builder getBuilder(Type type) {
        return new Builder(type);
    }

    private final List<ConstraintElement> ranges = new ArrayList<>();
    private final Type type;
    private boolean variableSize;

    public enum Type {
        SIZE,
        VALUE,
        FROM;
    }

    private Constraint(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    Parsed extract(int[] oidElements) {
        Parsed tryExtract = new Parsed();
        if (type != Type.SIZE) {
            tryExtract.content = Arrays.copyOf(oidElements, 1);
            if (oidElements.length > 1) {
                tryExtract.next = Arrays.copyOfRange(oidElements, 1, oidElements.length);
            }
        } else {
            for(ConstraintElement i: ranges) {
                if (variableSize) {
                    int elementSize = oidElements[0];
                    if (elementSize == 0) {
                        tryExtract.content = new int[0];
                        tryExtract.next = oidElements;
                    }
                    if (oidElements.length >= elementSize) {
                        tryExtract.content = Arrays.copyOfRange(oidElements, 1, elementSize + 1);
                        if (elementSize + 1 <= oidElements.length) {
                            tryExtract.next = Arrays.copyOfRange(oidElements, elementSize + 1, oidElements.length);
                        } else {
                            tryExtract.next = null;
                        }
                    }
                } else if (i.value instanceof Number && oidElements.length == ((Number) i.value).intValue()) {
                    tryExtract.content = oidElements;
                    tryExtract.next = null;
                    return tryExtract;
                } else if (i.value instanceof Number && oidElements.length > ((Number) i.value).intValue()) {
                    int val = ((Number) i.value).intValue();
                    tryExtract.content = Arrays.copyOf(oidElements, val);
                    if(val + 1 <= oidElements.length) {
                        tryExtract.next = Arrays.copyOfRange(oidElements, val, oidElements.length);
                    } else {
                        tryExtract.next = null;
                    }
                }
            }
        }
        return tryExtract;
    }

    @Override
    public String toString() {
        return ranges.toString();
    }

}
