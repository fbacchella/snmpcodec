package fr.jrds.snmpcodec.smi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fr.jrds.snmpcodec.smi.Index.Parsed;

public class Constraint {

    public static class ConstraintElement {
        public final Number value;
        public final Number from;
        public final Number to;

        public ConstraintElement(Number value) {
            this.value = value;
            this.from = null;
            this.to = null;
        }
        public ConstraintElement(Number from, Number to) {
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

    private final List<ConstraintElement> ranges = new ArrayList<>();
    private final boolean size;
    private boolean variableSize;

    public Constraint(boolean size) {
        this.size = size;
    }

    public void add(ConstraintElement newElement) {
        this.ranges.add(newElement);
    }

    Parsed extract(int[] oidElements) {
        Parsed tryExtract = new Parsed();
        if (! size) {
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
                } else if (oidElements.length == i.value.intValue()) {
                    tryExtract.content = oidElements;
                    tryExtract.next = null;
                    return tryExtract;
                } else if (oidElements.length > i.value.intValue()) {
                    tryExtract.content = Arrays.copyOf(oidElements, i.value.intValue());
                    if(i.value.intValue() + 1 <= oidElements.length) {
                        tryExtract.next = Arrays.copyOfRange(oidElements, i.value.intValue(), oidElements.length);
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

    public void finish() {
        if (ranges.size() > 1) {
            this.variableSize = true;
        } else if (ranges.get(0).value == null) {
            this.variableSize = true;
        } else {
            this.variableSize = false;
        }
    }

}
