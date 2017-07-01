package fr.jrds.snmpcodec.objects;

import java.util.HashMap;

public class Symbol {

    public static class SymbolMap<V> extends HashMap<Symbol, V> {

        @Override
        public V get(Object key) {
            Symbol skey = (Symbol) key;
            if (super.containsKey(skey)) {
                return super.get(skey);
            } else {
                Symbol generikkey = new Symbol(skey.name);
                return super.get(generikkey);
            }
        }

        @Override
        public boolean containsKey(Object key) {
            Symbol skey = (Symbol) key;
            if (!super.containsKey(key)) {
                return super.containsKey(new Symbol(skey.name));
            } else {
                return true;
            }
        }

    }

    public final String module;
    public final String name;
    public Symbol(String module, String name) {
        this.module = module;
        this.name = name;
    }
    public Symbol(String name) {
        int separator = name.indexOf('.');
        if (separator > 0) {
            this.module = name.substring(0, separator);
        } else {
            this.module = null;
        }
        // If '.' is not found, separator = -1 +1 it return 0, hence the start
        this.name = name.substring(separator + 1);
    }
    @Override
    public String toString() {
        return (module != null ? module : "") + "::" + name;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((module == null) ? 0 : module.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        Symbol other = (Symbol) obj;
        if(name == null) {
            if(other.name != null)
                return false;
        } else if(!name.equals(other.name))
            return false;
        // If this.module is null, it accepts any module
        if(module != null && !module.equals(other.module)) {
            return false;
        }
        return true;
    }
}
