package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibException;

public class AnnotedSyntax extends Syntax implements SyntaxContainer {

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

    @Override
    public String toString() {
        return super.toString() + " -> " + syntax;
    }

    @Override
    public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
        return syntax.getTextualConvention(hint, type);
    }

    @Override
    public void resolve(Map<Symbol, Syntax> types) throws MibException {
        syntax.resolve(types);
    }

}
