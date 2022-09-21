package fr.jrds.snmpcodec.parsing;

import java.util.Map;

import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

public class NullSyntax extends Syntax {

    public NullSyntax() {
        super(null, null);
    }

    @Override
    public String format(Variable v) {
        return "";
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

    @Override
    public void resolve(Map<Symbol, Syntax> types) {
        // Nothing to resolve with null syntax
    }

}
