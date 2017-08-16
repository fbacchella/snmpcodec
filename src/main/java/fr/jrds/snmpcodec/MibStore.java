package fr.jrds.snmpcodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.log.LogAdapter;
import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Syntax;

public abstract class MibStore {

    LogAdapter logger = LogAdapter.getLogger(MibStore.class);

    public final OidTreeNode top;
    public final Map<String, List<OidTreeNode>> names;
    public final Map<OidTreeNode, Syntax> syntaxes;
    public final Map<OidTreeNode, ObjectType> objects ;
    public final Map<OidTreeNode, Map<Integer, Map<String, Object>>> resolvedTraps;
    public final Set<String> modules;


    protected MibStore(OidTreeNode top, Set<String> modules,
            Map<String, List<OidTreeNode>> names, Map<OidTreeNode, Syntax> syntaxes, Map<OidTreeNode, ObjectType> objects, 
            Map<OidTreeNode, Map<Integer, Map<String, Object>>> resolvedTraps) {

        Map<OidTreeNode, Syntax> _syntaxes = new HashMap<>();
        Map<OidTreeNode, ObjectType> _objects = new HashMap<>();
        Map<OidTreeNode, Map<Integer, Map<String, Object>>> _resolvedTraps = new HashMap<>();
        Set<String> _modules = new HashSet<>();
        Map<String, List<OidTreeNode>> _names = new HashMap<>();

        _syntaxes.putAll(syntaxes);
        _objects.putAll(objects);
        _resolvedTraps.putAll(resolvedTraps);
        _modules.addAll(modules);
        _names.putAll(names);

        this.syntaxes = Collections.unmodifiableMap(_syntaxes);
        this.objects = Collections.unmodifiableMap(_objects);
        this.resolvedTraps = Collections.unmodifiableMap(_resolvedTraps);
        this.modules = Collections.unmodifiableSet(_modules);
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
            Map<String, Object> trap = resolvedTraps.get(s).get(variable.toInt());
            if (trap == null) {
                return null;
            } else {
                trap.get("SYMBOL").toString();
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
