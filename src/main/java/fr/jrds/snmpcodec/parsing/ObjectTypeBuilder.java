package fr.jrds.snmpcodec.parsing;

import java.util.List;
import java.util.Map;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

public class ObjectTypeBuilder {

    private final Syntax syntax;
    private final boolean indexed;
    private final IndexBuilder index;

    @SuppressWarnings("unchecked")
    public ObjectTypeBuilder(Map<String, Object> attributes) {
        syntax = (Syntax) attributes.remove("SYNTAX");
        this.indexed = attributes.containsKey("INDEX");
        if (indexed) {
            this.index = new IndexBuilder((List<Symbol>)attributes.remove("INDEX"));
        } else {
            this.index = null;
        }
    }

    public ObjectType resolve(MibStore store, OidTreeNode node) {
        Index newIndex = index != null ? index.resolve(store) : null;
        return new ObjectType(store.checkSyntax(node, syntax), indexed, newIndex);
    }

    public boolean isIndexed() {
        return indexed;
    }

}
