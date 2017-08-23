package fr.jrds.snmpcodec.parsing;

import java.util.List;
import java.util.Map;

import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

public class ObjectTypeBuilder {

    private final Syntax syntax;
    private final boolean indexed;
    private final IndexBuilder index;

    @SuppressWarnings("unchecked")
    ObjectTypeBuilder(Map<String, Object> attributes) {
        syntax = (Syntax) attributes.remove("SYNTAX");
        indexed = attributes.containsKey("INDEX");
        if (indexed) {
            List<Symbol> indexSymbols = (List<Symbol>)attributes.remove("INDEX");
            index = new IndexBuilder(indexSymbols);
        } else {
            index = null;
        }
    }

    ObjectType resolve(MibLoader loader) {
        loader.resolve(syntax);
        Index newIndex = index != null ? index.resolve(loader) : null;
        return new ObjectType(syntax, indexed, newIndex);
    }

    boolean isIndexed() {
        return indexed;
    }

}
