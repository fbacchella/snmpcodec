package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibException;

public class Referenced extends Syntax implements SyntaxContainer {

    private Syntax ref;
    private Symbol symbol; 

    public Referenced(Symbol symbol) {
        super(null, null);
        this.symbol = symbol;
    }

    @Override
    public String format(Variable v) {
        if (this.isNamed()) {
            return getNameFromNumer(v.toInt());
        } else {
            return ref.format(v);
        }
    }

    @Override
    public Variable parse(String text) {
        if (isNamed()) {
            return ref.getVariable(getNumberFromName(text));
        } else {
            return ref.parse(text);
        }
    }

    @Override
    public Object convert(Variable v) {
        return ref.convert(v);
    }

    @Override
    public Variable getVariable(Object source) {
        return ref.getVariable(source);
    }

    @Override
    public Variable getVariable() {
        return ref.getVariable();
    }

    @Override
    public Constraint getConstrains() {
        return ref.getConstrains();
    }

    @Override
    public String toString() {
        return ref != null ? ref.toString() : (symbol != null ? symbol.toString() : "not found");
    }

    @Override
    public Syntax getSyntax() {
        return ref;
    }

    @Override
    public TextualConvention getTextualConvention(String hint, Syntax type) throws MibException {
        return ref.getTextualConvention(hint, type);
    }

    @Override
    public boolean resolve(Map<Symbol, Syntax> types) {
        if (ref != null) {
            return true;
        } else if (types.containsKey(symbol)) {
            ref = types.get(symbol);
            symbol = null;
            return true;
        } else {
            return false;
        }
    }

};
