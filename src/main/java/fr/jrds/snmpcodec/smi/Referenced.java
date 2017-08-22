package fr.jrds.snmpcodec.smi;

import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;

public class Referenced extends Syntax {
    private OidTreeNode ref;
    private MibStore store;
    private Symbol symbol; 

    public Referenced(Symbol symbol) {
        super(null, null);
        this.symbol = symbol;
    }

    public OidTreeNode getNode() {
        return ref;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public void resolve(OidTreeNode node, MibStore store) {
        this.symbol = null;
        this.store = store;
        this.ref = node;
    }

    @Override
    public String format(Variable v) {
        if (this.isNamed()) {
            return getNameFromNumer(v.toInt());
        } else {
            return store.syntaxes.get(ref).format(v);
        }
    }

    @Override
    public Variable parse(String text) {
        if (isNamed()) {
            return store.syntaxes.get(ref).getVariable(getNumberFromName(text));
        } else {
            return store.syntaxes.get(ref).parse(text);
        }
    }

    @Override
    public Object convert(Variable v) {
        return store.syntaxes.get(ref).convert(v);
    }

    @Override
    public Variable getVariable(Object source) {
        return store.syntaxes.get(ref).getVariable(source);
    }

    @Override
    public Variable getVariable() {
        return store.syntaxes.get(ref).getVariable();
    }

    @Override
    public Constraint getConstrains() {
        return store.syntaxes.get(ref).getConstrains();
    }

    @Override
    public String toString() {
        return ref != null ? ref.toString() : (symbol != null ? symbol.toString() : "not found"); //store.syntaxes.get(ref).toString();
    }

};
