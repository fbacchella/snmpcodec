package fr.jrds.snmpcodec;

import fr.jrds.snmpcodec.smi.Symbol;

public class MibException extends Exception {

    public class NonCheckedMibException extends RuntimeException {
        public MibException getWrapper() {
            return MibException.this;
        }

        @Override
        public String getMessage() {
            return  MibException.this.getMessage();
        }
    }

    public static class DuplicatedModuleException extends MibException {

        private final String module;
        public DuplicatedModuleException(String module) {
            super("Duplicated module " + module);
            this.module = module;
        }
        /**
         * @return the module
         */
        public String getModule() {
            return module;
        }
    }

    public static class DuplicatedSymbolException extends MibException {

        private final Symbol symbol;
        public DuplicatedSymbolException(Symbol symbol) {
            super("Duplicated symbol " + symbol);
            this.symbol = symbol;
        }
        /**
         * @return the module
         */
        public Symbol getSymbol() {
            return symbol;
        }
    }

    public static class DuplicatedSymbolOid extends MibException {

        private final String oid;
        public DuplicatedSymbolOid(String oid) {
            super("Duplicated OID " + oid);
            this.oid = oid;
        }
        /**
         * @return the module
         */
        public String getSymbol() {
            return oid;
        }
    }

    public MibException(String message) {
        super(message);
    }

    public NonCheckedMibException getNonChecked() {
        throw new NonCheckedMibException();
    }

}
