package fr.jrds.snmpcodec;

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

    public MibException(String format) {
        super(format);
    }

    public NonCheckedMibException getNonChecked() {
        throw new NonCheckedMibException();
    }

}
