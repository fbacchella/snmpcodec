package fr.jrds.snmpcodec.smi;

import java.util.Collections;
import java.util.Map;

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
    public final Constraint constraints;

    protected DeclaredType(CONTENT content, Map<Number, String> names, Constraint constraints) {
        this.content = content;
        if (names != null) {
            this.names = Collections.unmodifiableMap(names);
        } else {
            this.names = null;
        }
        this.constraints = constraints;
    }

    public CONTENT getContent() {
        return content;
    }
    
    public abstract Codec getCodec(MibStore stort);

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
    };

    public static class SequenceOf extends DeclaredType<DeclaredType<?>> {
        public SequenceOf(DeclaredType<?> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Sequenceof;
        }
        @Override
        public Codec getCodec(MibStore stort) {
            return null;
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
    };

}
