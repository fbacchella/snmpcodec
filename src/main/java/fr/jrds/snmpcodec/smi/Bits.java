package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

public class Bits extends Syntax implements ProvidesTextualConvention {

    public Bits(Map<String, Integer> map, Constraint constraints) {
        super(null, constraints);
    }

    @Override
    public String format(Variable v) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object convert(Variable v) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Variable parse(String text) {
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

    @Override
    public TextualConvention getTextualConvention(String hint, Syntax type) {
        // TODO Auto-generated method stub
        return null;
    }

}
