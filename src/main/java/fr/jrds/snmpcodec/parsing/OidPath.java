package fr.jrds.snmpcodec.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.smi.Oid;

public class OidPath extends ArrayList<OidPath.OidComponent> {

    static public class OidComponent {
        public final int number;
        public final String name;
        public OidComponent(String name, int number) {
            this.number = number;
            this.name = name;
        }
        @Override
        public String toString() {
            return String.format("%s%d%s", name !=null ? name + "(": "", 
                    number,
                    name != null ? ")": "");
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + number;
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if(this == obj)
                return true;
            if(obj == null)
                return false;
            if(getClass() != obj.getClass())
                return false;
            OidComponent other = (OidComponent) obj;
            if(name == null) {
                if(other.name != null)
                    return false;
            } else if(!name.equals(other.name))
                return false;
            if(number != other.number)
                return false;
            return true;
        }
    }

    Symbol root;

    OidPath() {
        super();
    }

    @Override
    public String toString() {
        return (root != null ? root  + "." : "")
                + stream().map( i -> i.toString()).collect(Collectors.joining("."));
    }

    Symbol getRoot() {
        return root;
    }

    public List<OidPath.OidComponent> getComponents() {
        return new ArrayList<>(this);
    }

    public Stream<Oid> getAll(boolean tableEntry) {
        List<OidPath.OidComponent> stack = new ArrayList<>(this.size());
        return stream().map(i -> {
            try {
                List<OidPath.OidComponent> newPath = new ArrayList<>(stack.size());
                stack.add(i);
                newPath.addAll(stack);
                return new Oid(root, newPath, i.name);
            } catch (MibException e) {
                throw e.getNonChecked();
            }
        }).limit(this.size() - 1);
    }

}
