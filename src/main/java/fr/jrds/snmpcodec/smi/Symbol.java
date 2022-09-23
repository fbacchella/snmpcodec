package fr.jrds.snmpcodec.smi;

public class Symbol {

    public final String module;
    public final String name;

    public Symbol(String module, String name) {
        if (name == null) {
            throw new IllegalArgumentException("Invalid symbol");
        }
        this.module = module.intern();
        this.name = name.intern();
    }

    public Symbol(String name) {
        int separator = name.indexOf('.');
        if (separator > 0) {
            this.module = name.substring(0, separator).intern();
        } else {
            this.module = null;
        }
        // If '.' is not found, separator = -1 +1 it returns 0, hence the start
        this.name = name.substring(separator + 1).intern();
    }

    @Override
    public String toString() {
        return ((module != null ? module  +".": "") + name).intern();
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
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if ( obj.getClass() != Symbol.class) {
            return false;
        }
        Symbol other = (Symbol) obj;
        if (module == null) {
            return other.module == null && name.equals(other.name);
        }
        return name.equals(other.name) && module.equals(other.module);
    }

}
