package fr.jrds.snmpcodec.smi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.smi.AssignableFromInteger;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibStore;

public abstract class DeclaredType<CONTENT> {

    public enum AsnType {
        Native,
        Sequence,
        Sequenceof,
        Choice,
        Bits,
        Referenced,
        ObjectType,
    };

    public final CONTENT content;
    public final Map<Number, String> names;
    public final Map<String, Number> namesValue;
    protected final Constraint constraints;

    protected DeclaredType(CONTENT content, Map<Number, String> names, Constraint constraints) {
        this.content = content;
        if (names != null) {
            this.names = Collections.unmodifiableMap(names);
        } else {
            this.names = Collections.emptyMap();
        }
        Map<String, Number> namesValueTemp;
        if (this.names.size() > 0) {
            namesValueTemp = new HashMap<>(this.names.size());
            names.forEach( (i,j) -> namesValueTemp.put(j, i));
            this.namesValue = Collections.unmodifiableMap(namesValueTemp);
        } else {
            this.namesValue = Collections.emptyMap();
        }
        this.constraints = constraints;
    }

    public CONTENT getContent() {
        return content;
    }

    public abstract Codec getCodec(MibStore store);
    public abstract boolean isCodec();

    public abstract AsnType getType();

    @Override
    public String toString() {
        return getClass().getName().replace("fr.jrds.snmpcodec.smi.", "") + "[" + content + "]";
    }

    public static class Native extends DeclaredType<SmiType> {
        public Native(SmiType content, Map<Number, String> names, Constraint constraints) {
            super(content, names, constraints);
        }
        public AsnType getType() {
            return AsnType.Native;
        }
        @Override
        public Codec getCodec(MibStore stort) {
            return content;
        }
        public boolean isCodec() {
            return true;
        }

    };

    public static class Sequence extends DeclaredType<Map<String, DeclaredType<?>>> {
        public Sequence(Map<String, DeclaredType<?>> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Sequence;
        }
        @Override
        public Codec getCodec(MibStore stort) {
            return null;
        }
        public boolean isCodec() {
            return false;
        }
    };

    public static class SequenceOf extends DeclaredType<DeclaredType<?>> {
        public SequenceOf(DeclaredType<?> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Sequenceof;
        }
        @Override
        public Codec getCodec(MibStore store) {
            return null;
        }
        public boolean isCodec() {
            return false;
        }
    };

    public static class Choice extends DeclaredType<Map<String, DeclaredType<?>>> {
        public Choice(Map<String, DeclaredType<?>> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Choice;
        }
        @Override
        public Codec getCodec(MibStore stort) {
            return null;
        }
        public boolean isCodec() {
            return false;
        }
   };

    public static class Bits extends DeclaredType<Map<String, Integer>> {
        public Bits(Map<String, Integer> content, Map<Number, String> names, Constraint constraints) {
            super(content, names, constraints);
        }
        public AsnType getType() {
            return AsnType.Bits;
        }
        @Override
        public Codec getCodec(MibStore store) {
            return null;
        }
        public boolean isCodec() {
            return false;
        }
    };

    public static class Referenced extends DeclaredType<Symbol> {
        public Referenced(Symbol content, Map<Number, String> names, Constraint constraints) {
            super(content, names, constraints);
        }
        public AsnType getType() {
            return AsnType.Referenced;
        }
        @Override
        public Codec getCodec(MibStore store) {
            return store.codecs.get(content);
        }
        public boolean isCodec() {
            return true;
        }
        @Override
        public Constraint getConstraints() {
            if (constraints != null) {
                return constraints;
            } else {
                return null;
            }
        }
        
     };

    public static class ObjectType extends DeclaredType<ObjectTypeMacro> {
        public ObjectType(ObjectTypeMacro content) {
            super(content, Collections.emptyMap(), null);
        }
        public AsnType getType() {
            return AsnType.ObjectType;
        }
        public Codec getCodec(MibStore store) {
            return content;
        }
        public boolean isCodec() {
            return ! content.isTable();
        }
    };

    private static final Pattern NAMEDINDEXFORMAT = Pattern.compile("(?<name>\\p{L}(?:\\p{L}|\\d)+)\\((?<value>\\d+)?\\)");
    public boolean parseName(String text, Variable store) {
        if (namesValue !=null && namesValue.size() == 0) {
            return false;
        }
        if (! (store instanceof AssignableFromInteger)) {
            return false;
        }
        AssignableFromInteger dest = (AssignableFromInteger) store;
        Matcher m = NAMEDINDEXFORMAT.matcher(text);
        if (! m.matches()) {
            return false;
        }
        if (m.group("value") != null) {
            dest.setValue(Integer.parseInt(m.group("value")));
            return true;
        } else if (m.group("name") != null) {
            Number parsed = namesValue.get(m.group("name"));
            dest.setValue(parsed.intValue());
            return true;
        }
        return false;
    }

    /**
     * @return the constraints
     */
    public Constraint getConstraints() {
        return constraints;
    }

}
