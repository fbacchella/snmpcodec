package fr.jrds.snmpcodec.smi;

import java.util.Collections;
import java.util.Map;

import org.snmp4j.smi.Variable;

public class TableEntry extends Syntax {

    private final Map<String, Syntax> rows;

    public TableEntry(Map<String, Syntax> rows) {
        super(null, null);
        this.rows = Collections.unmodifiableMap(rows);
    }

    @Override
    public String format(Variable v) {
        return null;
    }

    @Override
    public Object convert(Variable v) {
        return null;
    }

    @Override
    public Variable parse(String text) {
        return null;
    }

    @Override
    public Variable getVariable() {
        return null;
    }

    @Override
    public Variable getVariable(Object source) {
        return null;
    }

    /**
     * @return the rows
     */
    public Map<String, Syntax> getRows() {
        return rows;
    }

    @Override
    public String toString() {
        return "TableEntry " + this.rows.keySet();
    }

    @Override
    public boolean resolve(Map<Symbol, Syntax> types) {
        return true;
    }

}
