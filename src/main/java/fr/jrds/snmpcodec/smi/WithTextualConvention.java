package fr.jrds.snmpcodec.smi;

public interface WithTextualConvention {
    /**
     * @return a empty instance of the associated Variable type
     */
    public abstract TextualConvention getTextualConvention(String hint, Syntax type);

}
