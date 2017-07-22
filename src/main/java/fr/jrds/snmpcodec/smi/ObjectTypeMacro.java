package fr.jrds.snmpcodec.smi;

import java.util.List;
import java.util.Map;

import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.Variable;

public class ObjectTypeMacro implements Codec {

    private final DeclaredType<?> syntax;
    private final Map<Symbol, Codec> codecs;
    private final boolean indexed;
    private final Index index;

    @SuppressWarnings("unchecked")
    public ObjectTypeMacro(Map<String, Object> attributes, Map<Symbol, Codec> codecs) {
        this.codecs = codecs;
        syntax = (DeclaredType<?>) attributes.remove("SYNTAX");
        this.indexed = attributes.containsKey("INDEX");
        this.index = new Index((List<Symbol>)attributes.remove("INDEX"));
    }

    @Override
    public String format(Variable v) {
        if(syntax.names != null && syntax.names.size() > 0 && v.getSyntax() == SMIConstants.SYNTAX_INTEGER && syntax.names.containsKey(v.toInt())) {
            return syntax.names.get(v.toInt());
        }
        switch (syntax.getType()) {
        case Native: {
            SmiType st = (SmiType) syntax.getContent();
            return st.format(v);
        }
        case Referenced: {
            Symbol ref = (Symbol) syntax.getContent();
            return codecs.get(ref).format(v);
        }
        default: {
            return null;
        }
        }
    }

    @Override
    public Variable parse(String text) {
        switch (syntax.getType()) {
        case Native: {
            SmiType st = (SmiType) syntax.getContent();
            return st.parse(text);
        }
        case Referenced: {
            Symbol ref = (Symbol) syntax.getContent();
            return codecs.get(ref).parse(text);
        }
        default: {
            return null;
        }
        }
    }

    @Override
    public boolean isIndex() {
        return this.indexed;
    }

    @Override
    public Index getIndex() {
        return index;
    }

    @Override
    public Variable getVariable() {
        switch (syntax.getType()) {
        case Native: {
            SmiType st = (SmiType) syntax.getContent();
            return st.getVariable();
        }
        case Referenced: {
            Symbol ref = (Symbol) syntax.getContent();
            return codecs.get(ref).getVariable();
        }
        default: {
            return null;
        }
        }
    }

    @Override
    public Object convert(Variable v) {
        switch (syntax.getType()) {
        case Native: {
            SmiType st = (SmiType) syntax.getContent();
            return st.convert(v);
        }
        case Referenced: {
            Symbol ref = (Symbol) syntax.getContent();
            return codecs.get(ref).convert(v);
        }
        default: {
            return null;
        }
        }
    }

    @Override
    public Constraint getConstrains() {
        return null;
    }

}
