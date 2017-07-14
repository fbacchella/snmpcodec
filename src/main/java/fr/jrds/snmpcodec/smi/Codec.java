package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

public interface Codec {
    
    public static class Referenced implements Codec {
        private final Symbol sym;
        private final Map<Symbol, Codec> codecs;
        
        public Referenced(Symbol s, Map<Symbol, Codec> codecs) {
            this.sym = s;
            this.codecs = codecs;
        }
        
        @Override
        public String format(Variable v) {
            return codecs.get(sym).format(v);
        }

        @Override
        public Variable parse(String text) {
            return codecs.get(sym).parse(text);
        }
        
    };
    
    public String format(Variable v);
    public Variable parse(String text);
    public default boolean isIndex() {
        return false;
    }
    
}
