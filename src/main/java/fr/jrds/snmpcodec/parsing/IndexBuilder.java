package fr.jrds.snmpcodec.parsing;

import java.util.ArrayList;
import java.util.List;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.Symbol;

public class IndexBuilder {
    
    private final List<Symbol> indexesSymbol;
    
    public IndexBuilder(List<Symbol> indexes) {
        this.indexesSymbol = indexes;
    }

    public Index resolve(MibStore store) {
        List<OidTreeNode> indexes = indexesSymbol.stream()
                .map( i -> store.resolveToBuild(i))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return new Index(indexes);
    }

}
