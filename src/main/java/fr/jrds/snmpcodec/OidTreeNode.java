package fr.jrds.snmpcodec;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.snmp4j.smi.OID;

public class OidTreeNode {

    private final String name;
    private int[] oidElements;
    private final NavigableMap<Integer, OidTreeNode> childs = new TreeMap<Integer, OidTreeNode>();
    private final OidTreeNode root;
    private final boolean isTableEntry;
    private final OidTreeNode parent;

    OidTreeNode() {
        name = null;
        root = this;
        oidElements = new int[] {};
        isTableEntry = false;
        parent = null;
    }

    private OidTreeNode(OidTreeNode parent, int id, String name, boolean isTableEntry) {
        this.parent = parent;
        this.name = name;
        this.root = parent.root;
        parent.childs.put(id, this);
        this.oidElements = Arrays.copyOf(parent.oidElements, parent.oidElements.length + 1);
        this.oidElements[this.oidElements.length - 1] = id;
        this.isTableEntry = isTableEntry;
    }

    /**
     * Added a new node at the right place in the tree
     * @param symbol
     * @param isTableEntry 
     * @throws MibException 
     */
    public OidTreeNode add(int[] oidElements, String symbol, boolean isTableEntry) throws MibException {
        OidTreeNode found = find(oidElements);
        if ( found != null) {
            //already exists, don't add
            return found;
        }
        int[] elements = oidElements;
        int[] oidParent = Arrays.copyOf(elements, elements.length - 1);
        //Adding a first level child
        if(oidParent.length == 0) {
            return new OidTreeNode(root, elements[0], symbol, isTableEntry);
        } else {
            OidTreeNode parent = root.find(oidParent);
            if(parent != null) {
                return new OidTreeNode(parent, elements[elements.length - 1], symbol, isTableEntry);
            } else {
                String dottedOid = Arrays.stream(oidElements)
                        .mapToObj(i -> Integer.toString(i))
                        .collect(Collectors.joining("."));
                throw new MibException("adding orphan child " + symbol + " " + dottedOid);
            }
        }
    }

    /**
     * @return The node content
     */
    public String getSymbol() {
        return name;
    }

    public OidTreeNode search(int[] oid) {
        return search(oid, false);
    }

    private OidTreeNode search(int[] oid, boolean Strict) {
        if (root.childs.containsKey(oid[0])) {
            return root.childs.get(oid[0]).search(oid, 1);
        } else {
            return null;
        }
    }

    private OidTreeNode search(int[] oid, int level) {
        if (oid.length == level) {
            return this;
        } else {
            int key = oid[level];
            if (childs.containsKey(key)) {
                return childs.get(key).search(oid, level + 1);
            } else {
                return this;
            }
        }
    }

    public OidTreeNode find(int[] oid) {
        OidTreeNode found = search(oid, true);
        if (found != null && found.oidEquals(oid)) {
            return found;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return (oidElements != null ? Utils.dottedNotation(oidElements) : "" )+ "=" + (name !=null ? name.toString() : "");
    }

    public int[] getElements() {
        return Arrays.copyOf(oidElements, oidElements.length);
    }

    public OID getOID() {
        return new OID(oidElements);
    }

    public boolean oidEquals(int[] other) {
        return other != null && Arrays.equals(oidElements, other);
    }

    public Collection<OidTreeNode> childs() {
        return Collections.unmodifiableCollection(childs.values());
    }

    public OidTreeNode getTableEntry() {
        OidTreeNode curs = this;
        while( curs != null && ! curs.isTableEntry) {
            curs = curs.parent;
        }
        if (curs != null && curs.isTableEntry) {
            return curs;
        } else {
            return null;
        }
    }

}
