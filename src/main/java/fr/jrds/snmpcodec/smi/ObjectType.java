package fr.jrds.snmpcodec.smi;

import org.snmp4j.smi.Variable;

public class ObjectType {

    private final Syntax syntax;
    private final boolean indexed;
    private final Index index;

    public ObjectType(Syntax syntax, boolean indexed, Index index) {
        this.syntax = syntax;
        this.indexed = indexed;
        this.index = index;
    }

    public String format(Variable v) {
        return syntax.format(v);
    }

    public Variable parse(String text) {
        if (syntax.isNamed()) {
            return null;
        } else {
            return syntax.parse(text);
        }
    }

    public Syntax getSyntax() {
        return syntax;
    }

    public Variable getVariable() {
        return syntax.getVariable();
    }

    public Object convert(Variable v) {
        return null;
    }

    /**
     * @return the index
     */
    public Index getIndex() {
        return index;
    }

    /**
     * @return the indexed
     */
    public boolean isIndexed() {
        return indexed;
    }

}
