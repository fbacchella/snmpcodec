package fr.jrds.snmpcodec.smi;

import java.util.List;
import java.util.Map;

import org.snmp4j.smi.Variable;

public class ObjectType {

    private final Syntax syntax;
    private final boolean indexed;
    private final Index index;

    @SuppressWarnings("unchecked")
    public ObjectType(Map<String, Object> attributes) {
        syntax = (Syntax) attributes.remove("SYNTAX");
        this.indexed = attributes.containsKey("INDEX");
        this.index = new Index((List<Symbol>)attributes.remove("INDEX"));
    }


    public String format(Variable v) {
        if (syntax.isNamed()) {
            return syntax.getNameFromNumer(v.toInt());
        } else {
            return syntax.format(v);
        }
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
