package fr.jrds.snmpcodec.smi;

public interface ProvidesTextualConvention {

    public abstract TextualConvention getTextualConvention(String hint, Syntax type);

}
