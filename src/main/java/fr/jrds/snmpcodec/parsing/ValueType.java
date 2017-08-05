package fr.jrds.snmpcodec.parsing;

import java.util.List;

import fr.jrds.snmpcodec.smi.Oid.OidComponent;

abstract class ValueType<T> {

    final T value;
    ValueType(T value) {
        this.value = value;
    }
    @Override
    public String toString() {
        return value.toString();
    }

    static class OidValue extends ValueType<List<OidComponent>> {
        OidValue(List<OidComponent> value) {
            super(value);
        }
    }

    static class BooleanValue extends ValueType<Boolean> {
        BooleanValue(Boolean value) {
            super(value);
        }
    }

    static class StringValue extends ValueType<String> {
        StringValue(String value) {
            super(value);
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    static class IntegerValue extends ValueType<Number> {
        IntegerValue(Number value) {
            super(value);
        }
    }

}
