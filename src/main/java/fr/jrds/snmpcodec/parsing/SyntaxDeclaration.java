package fr.jrds.snmpcodec.parsing;

import java.util.Collections;
import java.util.Map;

import fr.jrds.snmpcodec.Mib;
import fr.jrds.snmpcodec.smi.Constraint;
import fr.jrds.snmpcodec.smi.SmiType;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

@Deprecated
abstract class SyntaxDeclaration<CONTENT> {

    public enum AsnType {
        Smi,
        Sequence,
        Sequenceof,
        Choice,
        Bits,
        Referenced,
    };

    public final CONTENT content;
    public final Map<Number, String> names;
    protected final Constraint constraints;

    protected SyntaxDeclaration(CONTENT content, Map<Number, String> names, Constraint constraints) {
        this.content = content;
        if (names != null) {
            this.names = Collections.unmodifiableMap(names);
        } else {
            this.names = Collections.emptyMap();
        }
        this.constraints = constraints;
    }

    public CONTENT getContent() {
        return content;
    }

    public abstract AsnType getType();

    @Override
    public String toString() {
        return getClass().getName().replace("fr.jrds.snmpcodec.smi.", "") + "[" + content + "]";
    }

    public static class Native extends SyntaxDeclaration<SmiType> {
        public Native(SmiType content, Map<Number, String> names, Constraint constraints) {
            super(content, names, constraints);
        }
        public AsnType getType() {
            return AsnType.Smi;
        }
        public boolean isCodec() {
            return true;
        }
        @Override
        public Syntax getSyntax(Mib store) {
            return content;
        }
    };

    public static class Sequence extends SyntaxDeclaration<Map<String, SyntaxDeclaration<?>>> {
        public Sequence(Map<String, SyntaxDeclaration<?>> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Sequence;
        }
        @Override
        public Syntax getSyntax(Mib store) {
            return null;
        }
    };

    public static class SequenceOf extends SyntaxDeclaration<SyntaxDeclaration<?>> {
        public SequenceOf(SyntaxDeclaration<?> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Sequenceof;
        }
        public boolean isCodec() {
            return false;
        }
        @Override
        public Syntax getSyntax(Mib store) {
            return null;
        }
    };

    public static class Choice extends SyntaxDeclaration<Map<String, SyntaxDeclaration<?>>> {
        public Choice(Map<String, SyntaxDeclaration<?>> content, Map<Number, String> names) {
            super(content, names, null);
        }
        public AsnType getType() {
            return AsnType.Choice;
        }
        @Override
        public Syntax getSyntax(Mib store) {
            return null;
        }
    };

    public static class Bits extends SyntaxDeclaration<Map<String, Integer>> {
        public Bits(Map<String, Integer> content, Map<Number, String> names, Constraint constraints) {
            super(content, names, constraints);
        }
        public AsnType getType() {
            return AsnType.Bits;
        }
        @Override
        public Syntax getSyntax(Mib store) {
            return null;
        }
    };

    public static class Referenced extends SyntaxDeclaration<Symbol> {
        public Referenced(Symbol content, Map<Number, String> names, Constraint constraints) {
            super(content, names, constraints);
        }
        public AsnType getType() {
            return AsnType.Referenced;
        }
        public boolean isCodec() {
            return true;
        }
        @Override
        public Syntax getSyntax(Mib store) {
            return new fr.jrds.snmpcodec.smi.Referenced(content, store, names, constraints);
        }

    };

    /**
     * @return the constraints
     */
    public Constraint getConstraints() {
        return constraints;
    }
    public abstract Syntax getSyntax(Mib store);

}
