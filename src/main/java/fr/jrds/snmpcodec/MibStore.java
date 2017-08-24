package fr.jrds.snmpcodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.Trap;

public abstract class MibStore {

    LogAdapter logger = LogAdapter.getLogger(MibStore.class);

    public final OidTreeNode top;
    public final Map<String, List<OidTreeNode>> names;
    public final Map<String, Syntax> syntaxes;
    public final Map<OidTreeNode, ObjectType> objects ;
    public final Map<OidTreeNode, Map<Integer, Trap>> resolvedTraps;
    public final Set<String> modules;


    protected MibStore(OidTreeNode top, Set<String> modules,
            Map<String, List<OidTreeNode>> names, Map<String, Syntax> syntaxes, Map<OidTreeNode, ObjectType> objects, 
            Map<OidTreeNode, Map<Integer, Trap>> _resolvedTraps) {

        this.syntaxes = Collections.unmodifiableMap(syntaxes);
        this.objects = Collections.unmodifiableMap(objects);
        this.resolvedTraps = Collections.unmodifiableMap(_resolvedTraps);
        this.modules = Collections.unmodifiableSet(modules);
        this.names = Collections.unmodifiableMap(names);

        this.top = top;
    }

    public Object[] parseIndexOID(int[] oid) {
        OidTreeNode found = top.search(oid);
        if(found == null) {
            return new Object[] {new OID(oid)};
        }
        List<Object> parts = new ArrayList<Object>();
        int[] foundOID = found.getElements();
        parts.add(found.getSymbol());
        //The full path was not found, try to resolve the left other
        if(foundOID.length < oid.length ) {
            OidTreeNode parent = top.find(Arrays.copyOf(foundOID, foundOID.length -1 ));
            if (parent != null) {
                ObjectType parentCodec = objects.get(parent);
                if(parentCodec.isIndexed()) {
                    Index idx = parentCodec.getIndex();
                    int[] index = Arrays.copyOfRange(oid, foundOID.length, oid.length);
                    Arrays.stream(idx.resolve(index, this)).forEach(i -> parts.add(i));
                }
            }
        }
        return parts.toArray(new Object[parts.size()]);
    }

    public boolean containsKey(String text) {
        return names.containsKey(text);
    }

    public int[] getFromName(String text) {
        if (names.containsKey(text)) {
            for(OidTreeNode s: names.get(text)) {
                return s.getElements();
            }
        }
        return null;
    }

    public String format(OID instanceOID, Variable variable) {
        OidTreeNode s = top.find(instanceOID.getValue());
        if (s == null) {
            return null;
        }
        else if (resolvedTraps.containsKey(s)) {
            Trap trap = resolvedTraps.get(s).get(variable.toInt());
            if (trap == null) {
                return null;
            } else {
                return trap.name;
            }
        } else if (objects.containsKey(s)) {
            ObjectType ot = objects.get(s);
            return ot.format(variable);
        }
        return null;
    }

    public Variable parse(OID instanceOID, String text) {
        OidTreeNode node = top.search(instanceOID.getValue());
        if (node == null) {
            return null;
        } else if (syntaxes.containsKey(node)) {
            return syntaxes.get(node).parse(text);
        }
        return null;
    }

}
