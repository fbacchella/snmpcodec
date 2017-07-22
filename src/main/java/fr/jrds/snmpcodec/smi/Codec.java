package fr.jrds.snmpcodec.smi;

import java.util.Map;

import org.snmp4j.smi.Variable;

public interface Codec {

    public static class Referenced implements Codec {
        private final Symbol sym;
        private final Map<Symbol, Codec> codecs;
        private final Constraint constraint;

        public Referenced(Symbol s, Map<Symbol, Codec> codecs) {
            this.sym = s;
            this.codecs = codecs;
            this.constraint = null;
        }

        public Referenced(Symbol s, Map<Symbol, Codec> codecs, Constraint constraint) {
            this.sym = s;
            this.codecs = codecs;
            this.constraint = constraint;
        }

        @Override
        public String format(Variable v) {
            return codecs.get(sym).format(v);
        }

        @Override
        public Variable parse(String text) {
            return codecs.get(sym).parse(text);
        }

        @Override
        public Variable getVariable() {
            return codecs.get(sym).getVariable();
        }

        @Override
        public Object convert(Variable v) {
            return codecs.get(sym).convert(v);
        }

        @Override
        public Constraint getConstrains() {
            return constraint;
        }

    };

    public String format(Variable v);
    public Variable parse(String text);
    public Variable getVariable();
    public Object convert(Variable v);
    public default boolean isIndex() {
        return false;
    }
    public default Index getIndex() {
        return null;
    }
    public Constraint getConstrains();

}
