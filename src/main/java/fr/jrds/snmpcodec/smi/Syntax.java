package fr.jrds.snmpcodec.smi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.smi.Variable;

public abstract class Syntax {

    private static final Pattern NAMEDINDEXFORMAT = Pattern.compile("(?<name>\\p{L}(?:\\p{L}|\\d)+)(?:\\s+\\((?<value>\\d+)?\\))?");

    private final Constraint constraints;
    private final Map<String, Integer> fromname;
    private final Map<Integer, String> toname;

    public Syntax(Map<Number, String> names, Constraint constraints) {
        if (names != null) {
            this.toname = new HashMap<>(names.size());
            this.fromname = new HashMap<>(names.size());
            names.entrySet().forEach(e -> {
                String name = e.getValue();
                Integer val = new Integer(e.getKey().intValue());
                toname.put(val, name);
                fromname.put(name, val);
            });
        } else {
            this.toname = Collections.emptyMap();
            this.fromname = Collections.emptyMap();
        }
        this.constraints = constraints;
    }

    public abstract String format(Variable v);
    public abstract Object convert(Variable v);
    public abstract Variable parse(String text);
    public abstract Variable getVariable();
    public abstract Variable getVariable(Object source);

    public Constraint getConstrains() {
        return constraints;
    }

    public boolean isNamed() {
        return toname.size() != 0;
    }

    public String getNameFromNumer(int number) {
        return this.toname.get(number);
    }

    public Integer getNumberFromName(String name) {
        Matcher m = NAMEDINDEXFORMAT.matcher(name);
        if (! m.matches()) {
            try {
                return Integer.parseInt(name);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (m.group("value") != null) {
            return Integer.parseInt(m.group("value"));
        } else if (m.group("name") != null) {
            return fromname.get(m.group("name"));
        } else {
            return null;
        }
    }

}
