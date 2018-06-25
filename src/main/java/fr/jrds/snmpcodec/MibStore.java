package fr.jrds.snmpcodec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.smi.Index;
import fr.jrds.snmpcodec.smi.ObjectType;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.Trap;

public abstract class MibStore {

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

    /**
     * Try to resolve the OID as a map.
     * <p>If the OID is a table entry, the map is {@link java.util.LinkedHashMap LinkedHashMap}. The keys are 
     * the table name, followed by column name. The values are the current column name followed by the index
     * values.</p>
     * If the OID is a single value, both the map's key and value contains the name of the OID.
     * @param oid to parse
     * @return The description of the OID.
     */
    public Map<String, Object> parseIndexOID(int[] oid) {
        OidTreeNode found = top.search(oid);
        if(found == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> parts;
        int[] foundOID = found.getElements();
        if(foundOID.length < oid.length ) {
            //The full path was not found, try to resolve the suffix as a value

            // It's not a table, MIB module missing, abort
            // Table check is needed, some broken implementations starts tables at index 0 (not allowed in RFC)
            if (found.getTableEntry() == null) {
                if (foundOID.length == oid.length  - 1 && oid[oid.length - 1] == 0) {
                    // Hey of course it was not a table, it was a oid value
                    return Collections.singletonMap(found.getSymbol(), found.getSymbol());
                } else {
                    //Don't throw uncompleted OID, store it as [String, [x, ...]]
                    int[] numberPart = Arrays.copyOfRange(oid, foundOID.length, oid.length);
                    return Collections.singletonMap(found.getSymbol(),new Object[] {found.getSymbol(), numberPart});
                }
            }
            parts = new LinkedHashMap<>();
            parts.put(found.getTableEntry().getSymbol(), found.getSymbol());
            OidTreeNode parent = top.find(Arrays.copyOf(foundOID, foundOID.length -1 ));
            if (parent != null) {
                ObjectType parentCodec = objects.get(parent);
                if(parentCodec.isIndexed()) {
                    Index idx = parentCodec.getIndex();
                    int[] index = Arrays.copyOfRange(oid, foundOID.length, oid.length);
                    idx.resolve(index, this).forEach((i,j) -> parts.put(i, j));
                }
            }
        } else {
            parts = Collections.singletonMap(found.getSymbol(), found.getSymbol());
        }
        return parts;
    }

    public boolean containsKey(String text) {
        return names.containsKey(text);
    }

    public int[] getFromName(String text) {
        return names.get(text).stream().findFirst().map(i -> i.getElements()).orElseGet(null);
    }

    public String format(OID instanceOID, Variable variable) {
        OidTreeNode node = top.search(instanceOID.getValue());
        if (node == null) {
            return null;
        }
        else if (resolvedTraps.containsKey(node)) {
            Trap trap = resolvedTraps.get(node).get(variable.toInt());
            if (trap == null) {
                return null;
            } else {
                return trap.name;
            }
        } else if (objects.containsKey(node)) {
            ObjectType ot = objects.get(node);
            return ot.format(variable);
        }
        return null;
    }

    public Variable parse(OID instanceOID, String text) {
        OidTreeNode node = top.search(instanceOID.getValue());
        if (node == null) {
            return null;
        } else if (syntaxes.containsKey(node.getSymbol())) {
            return syntaxes.get(node.getSymbol()).parse(text);
        } else if (objects.containsKey(node)) {
            Syntax s = objects.get(node).getSyntax();
            return s.parse(text);
        }
        return null;
    }

}
