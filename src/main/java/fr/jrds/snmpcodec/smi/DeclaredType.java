package fr.jrds.snmpcodec.smi;

import java.util.Map;

public abstract class DeclaredType<CONTENT> {

    public enum AsnType {
        Native,
        Sequence,
        Sequenceof,
        Choice,
        Bits,
        Referenced,
        TextualConvention,
        ObjectType,
    };

    public final CONTENT content;
    public final Map<Number, String> names;

    protected DeclaredType(CONTENT content, Map<Number, String> names) {
        this.content = content;
        this.names = names;
    }

    public CONTENT getContent() {
        return content;
    }

    public abstract AsnType getType();

    @Override
    public String toString() {
        return getClass().getName().replace("fr.jrds.snmpcodec.smi.", "") + "[" + content + "]";
    }

    public static class Native extends DeclaredType<SmiType> {
        public Native(SmiType content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.Native;
        }
    };

    public static class Sequence extends DeclaredType<Map<String, DeclaredType<?>>> {
        public Sequence(Map<String, DeclaredType<?>> content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.Sequence;
        }
    };

    public static class SequenceOf extends DeclaredType<DeclaredType<?>> {
        public SequenceOf(DeclaredType<?> content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.Sequenceof;
        }
    };

    public static class Choice extends DeclaredType<Map<String, DeclaredType<?>>> {
        public Choice(Map<String, DeclaredType<?>> content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.Choice;
        }

    };

    public static class Bits extends DeclaredType<Map<String, Integer>> {
        public Bits(Map<String, Integer> content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.Bits;
        }
    };

    public static class Referenced extends DeclaredType<Symbol> {
        public Referenced(Symbol content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.Referenced;
        }
    };

    public static class TextualConvention extends DeclaredType<DeclaredType<?>> {
        public TextualConvention(DeclaredType<?> content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.TextualConvention;
        }
    };

    public static class ObjectType extends DeclaredType<DeclaredType<?>> {
        public ObjectType(DeclaredType<?> content, Map<Number, String> names) {
            super(content, names);
        }
        public AsnType getType() {
            return AsnType.ObjectType;
        }
    };

}
