package fr.jrds.snmpcodec.parsing;

import java.util.List;
import java.util.Map;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

public class ObjectTypeBuilder {

    private final Syntax syntax;
    private final boolean indexed;
    private final boolean augments;
    private final IndexBuilder index;
    private final Symbol augmentedEntry;

    ObjectTypeBuilder(Map<String, Object> attributes) {
        syntax = (Syntax) attributes.remove("SYNTAX");
        indexed = attributes.containsKey("INDEX");
        augments = attributes.containsKey("AUGMENTS");
        if (indexed) {
            @SuppressWarnings("unchecked")
            List<Symbol> indexSymbols = (List<Symbol>)attributes.remove("INDEX");
            index = new IndexBuilder(indexSymbols);
        } else {
            index = null;
        }
        if (augments) {
            augmentedEntry = (Symbol) attributes.remove("AUGMENTS");
        } else {
            augmentedEntry = null;
        }
    }

    ObjectType resolve(MibLoader loader) throws MibException {
        loader.resolve(syntax);
        Index newIndex = null;
        if (index != null) {
            newIndex = index.resolve(loader);
        }
        return new ObjectType(syntax, indexed, newIndex);
    }

    ObjectType resolve(MibLoader loader, ObjectTypeBuilder augmented) throws MibException {
        loader.resolve(syntax);
        Index newIndex = null;
        if (augmented.index != null) {
            newIndex = augmented.index.resolve(loader);
        }
        return new ObjectType(syntax, augments, newIndex);
    }

    boolean isIndexed() {
        return indexed || augments;
    }

    boolean isAugmenter() {
        return augments;
    }

    Symbol getAugmented() {
        return augmentedEntry;
    }

}
