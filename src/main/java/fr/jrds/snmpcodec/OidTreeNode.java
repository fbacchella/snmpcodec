package fr.jrds.snmpcodec;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.snmp4j.smi.OID;

public abstract class OidTreeNode {

    private final String name;
    protected int[] oidElements;
    private final NavigableMap<Integer, OidTreeNode> childs = new TreeMap<>();
    protected final OidTreeNode root;
    private final boolean isTableEntry;
    private final OidTreeNode parent;

    protected OidTreeNode() {
        name = null;
        root = this;
        oidElements = new int[] {};
        isTableEntry = false;
        parent = null;
    }

    protected OidTreeNode(OidTreeNode parent, int id, String name, boolean isTableEntry) {
        this.parent = parent;
        this.name = name;
        this.root = parent.root;
        parent.childs.put(id, this);
        this.oidElements = Arrays.copyOf(parent.oidElements, parent.oidElements.length + 1);
        this.oidElements[this.oidElements.length - 1] = id;
        this.isTableEntry = isTableEntry;
    }

    /**
     * @return The node content
     */
    public String getSymbol() {
        return name;
    }

    public OidTreeNode getParent() {
        return parent;
    }

    public OidTreeNode search(int[] oid) {
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
        OidTreeNode found = search(oid);
        if (found != null && found.oidEquals(oid)) {
            return found;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return (oidElements != null ? Utils.dottedNotation(oidElements) : "" )+ "=" + (name !=null ? name : "");
    }

    public int[] getElements() {
        return Arrays.copyOf(oidElements, oidElements.length);
    }

    protected int[] getElementsPrivate() {
        return oidElements;
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
            return curs.parent;
        } else {
            return null;
        }
    }

}
