package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

public class Table extends Syntax {

    private final Symbol row;

    public Table(Symbol row) {
        super(null, null);
        this.row = row;
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
     * @return the row
     */
    public Symbol getRow() {
        return row;
    }

}
