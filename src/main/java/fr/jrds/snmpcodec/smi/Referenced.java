package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.Mib;

public class Referenced extends Syntax {
    private final Symbol sym;
    public Symbol getSym() {
        return sym;
    }

    private final Mib store;

    public Referenced(Symbol s, Mib store, Map<Number, String> names, Constraint constraints) {
        super(names, constraints);
        this.sym = s;
        this.store = store;
    }

    @Override
    public String format(Variable v) {
        if (this.isNamed()) {
            return this.getNameFromNumer(v.toInt());
        } else {
            return store.codecs.get(sym).format(v);
        }
    }

    @Override
    public Variable parse(String text) {
        if (this.isNamed()) {
            return store.codecs.get(sym).getVariable(getNumberFromName(text));
        } else {
            return store.codecs.get(sym).parse(text);
        }
    }

    @Override
    public Object convert(Variable v) {
        return store.codecs.get(sym).convert(v);
    }

    @Override
    public Variable getVariable(Object source) {
        return store.codecs.get(sym).getVariable(source);
    }

    @Override
    public Variable getVariable() {
        return store.codecs.get(sym).getVariable();
    }

};
