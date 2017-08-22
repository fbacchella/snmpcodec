package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

public class AnnotedSyntax extends Syntax {

    private final Syntax syntax;

    public AnnotedSyntax(Syntax syntax, Map<Number, String> names, Constraint constraints) {
        super(names, constraints);
        this.syntax = syntax;
    }

    @Override
    public String format(Variable v) {
        if (isNamed()) {
            return getNameFromNumer(v.toInt());
        } else {
            return syntax.format(v);
        }
    }

    @Override
    public Object convert(Variable v) {
        if (isNamed()) {
            return getNameFromNumer(v.toInt());
        } else {
            return syntax.convert(v);
        }
    }

    @Override
    public Variable parse(String text) {
        if (this.isNamed()) {
            return syntax.getVariable(getNumberFromName(text));
        } else {
            return syntax.parse(text);
        }
    }

    @Override
    public Variable getVariable() {
        return syntax.getVariable();
    }

    @Override
    public Variable getVariable(Object source) {
        return syntax.getVariable(source);
    }

    public Syntax getSyntax() {
        return syntax;
    }

}
