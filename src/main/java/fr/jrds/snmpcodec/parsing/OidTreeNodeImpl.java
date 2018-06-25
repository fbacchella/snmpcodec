package fr.jrds.snmpcodec.parsing;

import java.util.Arrays;

import fr.jrds.snmpcodec.OidTreeNode;

public class OidTreeNodeImpl extends OidTreeNode {

    OidTreeNodeImpl() {
        super();
    }

    OidTreeNodeImpl(OidTreeNode parent, int i, String symbol, boolean isTableEntry) {
        super(parent, i, symbol, isTableEntry);
    }

    /**
     * Added a new node at the right place in the tree
     * @param symbol
     * @param isTableEntry 
     */
    OidTreeNode add(int[] oidElements, String symbol, boolean isTableEntry) {
        OidTreeNode found = find(oidElements);
        if ( found != null) {
            //already exists, don't add
            return found;
        }
        int[] elements = oidElements;
        int[] oidParent = Arrays.copyOf(elements, elements.length - 1);
        //Adding a first level child
        if(oidParent.length == 0) {
            return new OidTreeNodeImpl(root, elements[0], symbol, isTableEntry);
        } else {
            OidTreeNode parent = root.find(oidParent);
            if(parent != null) {
                return new OidTreeNodeImpl(parent, elements[elements.length - 1], symbol, isTableEntry);
            } else {
                // Missing intermediary steps, add them
                // The type cast is needed because search can be called from a OidTreeNode
                int[] closer = ((OidTreeNodeImpl)search(oidElements)).getElementsPrivate();
                for (int i=closer.length; i < oidElements.length -1 ; i++) {
                    int[] missing = Arrays.copyOf(oidElements, i);
                    OidTreeNode missingParent = root.find(missing);
                    parent = new OidTreeNodeImpl(missingParent, elements[i], null, false);
                }
                return new OidTreeNodeImpl(parent, elements[elements.length - 1], symbol, isTableEntry);
            }
        }
    }

}
