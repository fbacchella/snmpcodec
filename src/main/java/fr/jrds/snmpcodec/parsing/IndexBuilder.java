package fr.jrds.snmpcodec.parsing;

import java.util.ArrayList;
import java.util.List;

import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.Symbol;

class IndexBuilder {

    private final List<Symbol> indexesSymbol;

    IndexBuilder(List<Symbol> indexes) {
        this.indexesSymbol = indexes;
    }

    Index resolve(MibLoader loader) {
        List<OidTreeNode> indexes = indexesSymbol.stream()
                .map( i -> loader.resolveNode(i))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return new Index(indexes);
    }

}
