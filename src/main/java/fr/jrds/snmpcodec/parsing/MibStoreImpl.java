package fr.jrds.snmpcodec.parsing;

import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.Trap;

class MibStoreImpl extends MibStore {

    public MibStoreImpl(OidTreeNode top, Set<String> modules, Map<String, List<OidTreeNode>> names,
            Map<String, Syntax> syntaxes, Map<OidTreeNode, ObjectType> objects, Map<OidTreeNode, Map<Integer, Trap>> _resolvedTraps 
            ) {
        super(top, modules, names, syntaxes, objects, _resolvedTraps);
    }

}
