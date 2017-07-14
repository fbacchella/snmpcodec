package fr.jrds.snmpcodec.parsing;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;

import fr.jrds.snmpcodec.parsing.ASNParser.AssignmentContext;
import fr.jrds.snmpcodec.parsing.ASNParser.BitDescriptionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.BitsTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.BooleanValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ChoiceTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ComplexAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ComplexAttributContext;
import fr.jrds.snmpcodec.parsing.ASNParser.IntegerTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.IntegerValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.MacroAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleDefinitionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ObjIdComponentsListContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ReferencedTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SequenceOfTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SequenceTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.StringValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SymbolsFromModuleContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TextualConventionAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TypeAssignmentContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ValueAssignmentContext;
import fr.jrds.snmpcodec.smi.Constraint;
import fr.jrds.snmpcodec.smi.DeclaredType;
import fr.jrds.snmpcodec.smi.SmiType;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Oid.OidComponent;
import fr.jrds.snmpcodec.smi.Oid.OidPath;
import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.MibStore;

public class ModuleListener extends ASNBaseListener {

    static abstract class MibObject {

    };

    static class Import extends MibObject {
        public final String name;

        Import(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Imported from " + name;
        }

    };

    static class Macro extends MibObject {

        Macro() {
        }

        @Override
        public String toString() {
            return "Macro, ignored content";
        }

    };

    static class Value extends MibObject {
        public TypeDescription type;
        public Object value;

        Value(TypeDescription type, Object value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Value " + type + " ::= " + value;
        }

    };

    static class Type extends MibObject {
        public TypeDescription type;

        Type(TypeDescription type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type.toString();
        }

    };

    static class ObjectType extends MibObject {
        public String type;
        public Object value;

        ObjectType(String type, Object value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + " ::= " + value;
        }

    };

    static class MappedObject extends MibObject {
        String name;
        Map <String, Object> values = new HashMap<>();
        public MappedObject(String name) {
            this.name = name;
        }
        @Override
        public String toString() {
            return name + " " + values;
        }
    }

    static class StructuredObject<T> extends MappedObject {
        ValueType<T> value;
        public StructuredObject(String name) {
            super(name);
        }
        @Override
        public String toString() {
            return name + "/" + value + values;
        }
    }

    static class TextualConvention extends MappedObject {
        OidType oid;
        public TextualConvention() {
            super("TEXTUAL-CONVENTION");
        }
    }

    static class Dummy extends MibObject {
        public final int assignementType;

        Dummy(int assignementType) {
            this.assignementType = assignementType;
        }

        @Override
        public String toString() {
            String assignementName;
            switch (assignementType) {
            case ASNParser.RULE_typeAssignment: assignementName = "type assignement";
            case ASNParser.RULE_valueAssignment: assignementName = "value assignement";
            default: assignementName = "Assignement " + Integer.toString(assignementType);
            }
            return assignementName;
        }

    };

    static abstract class ValueType<T> {
        T value;
        @Override
        public String toString() {
            return value.toString();
        }
    }

    static class OidType extends ValueType<List<OidComponent>> {
        OidType(List<OidComponent> value) {
            this.value = value;
        }
    }

    static class BooleanValue extends ValueType<Boolean> {
    }

    static class StringValue extends ValueType<String> {
        public StringValue(String value) {
            this.value = value;
        }
        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    static class IntegerValue extends ValueType<Number> {
        IntegerValue(Number value) {
            this.value = value;
        }
    }

    enum BuiltinType {
        octetStringType,
        bitStringType,
        choiceType,
        enumeratedType,
        integerType,
        sequenceType,
        sequenceOfType,
        setType,
        setOfType,
        objectidentifiertype,
        objectClassFieldType,
        nullType,
        referencedType,
        bitsType
    }

    class TypeDescription {
        BuiltinType type;
        Object typeDescription = null;
        Map<Number, String> names;
        List<Constraint> constraint = null;

        @Override
        public String toString() {
            return "" + type + (typeDescription != null ? " " + typeDescription : "");
        }
        @SuppressWarnings("unchecked")
        public DeclaredType<?> resolve() {
            switch (type) {
            case referencedType:
                return new DeclaredType.Referenced(resolveSymbol((String) typeDescription), names);
            case octetStringType:
                return new DeclaredType.Native(SmiType.OctetString, names);
            case bitStringType:
                return new DeclaredType.Bits((Map<String, Integer>) typeDescription, names);
            case integerType:
                return new DeclaredType.Native(SmiType.INTEGER, names);
            case objectidentifiertype:
                return new DeclaredType.Native(SmiType.ObjID, names);
            case nullType:
                return new DeclaredType.Native(SmiType.Null, names);
            case sequenceType:
                return new DeclaredType.Sequence((Map<String, DeclaredType<?>>)typeDescription, names);
            case sequenceOfType:
                return new DeclaredType.SequenceOf((DeclaredType<?>) typeDescription, names);
            case choiceType:
                return new DeclaredType.Choice((Map<String, DeclaredType<?>>)typeDescription, names);
            case bitsType:
                return new DeclaredType.Bits((Map<String, Integer>)typeDescription, names);
            case enumeratedType:
            case objectClassFieldType:
            case setType:
            case setOfType:
                System.out.format("unmanaged type: %s\n", this);
                return new DeclaredType.Native(SmiType.Null, names);
            default:
                System.out.format("unchecked type: %s\n", this);
                return new DeclaredType.Native(SmiType.Null, names);
            }
        }
    }

    private final Deque<Object> stack = new ArrayDeque<>();

    Parser parser;

    Map<String, Object> objects = new HashMap<>();
    Map<String, String> importedFrom = new HashMap<>();
    Map<OidType, MibObject> symbols = new HashMap<>();

    String currentModule = null;

    private final MibStore store;


    public ModuleListener(MibStore store) {
        super();
        this.store = store;
    }

    private Symbol resolveSymbol(String name) {
        if (importedFrom.containsKey(name)) {
            return new Symbol(importedFrom.get(name), name);
        } else {
            return new Symbol(currentModule, name);
        }
    }

    @Override
    public void enterModuleDefinition(ModuleDefinitionContext ctx) {
        if (ctx.IDENTIFIER() == null) {
            throw new ModuleException("No module name", parser.getInputStream().getSourceName());
        }
        currentModule = ctx.IDENTIFIER().getText();
        objects.clear();
        importedFrom.clear();
        if ( ! store.newModule(currentModule)) {
            throw new ModuleException.DuplicatedMibException(currentModule, parser.getInputStream().getSourceName());
        }
    }

    @Override
    public void enterSymbolsFromModule(SymbolsFromModuleContext ctx) {
        Import imported = new Import(ctx.globalModuleReference().getText());
        ctx.symbolList().symbol().stream()
        .forEach( i->  {
            objects.put(i.getText(), imported);
            importedFrom.put(i.getText(), ctx.globalModuleReference().getText());

        });
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
        throw new ModuleException("Invalid assignement: " + node.getText(), parser.getInputStream().getSourceName(), node.getSymbol());
    }

    /****************************************
     * Manage assignemnts and push them on stack
     * assignements: objectTypeAssignement, valueAssignment, typeAssignment, textualConventionAssignement, macroAssignement
     ***************************************/

    @Override
    public void enterAssignment(AssignmentContext ctx) {
        stack.clear();
        stack.push(resolveSymbol(ctx.identifier.getText()));
    }

    @Override
    public void exitAssignment(AssignmentContext ctx) {
        stack.clear();
    }

    @Override
    public void enterComplexAssignement(ComplexAssignementContext ctx) {
        stack.push(new StructuredObject(ctx.macroName().getText()));
    }

    @Override
    public void exitComplexAssignement(ComplexAssignementContext ctx) {
        ValueType<?> value = (ValueType<?>) stack.pop();
        while ( ! (stack.peek() instanceof StructuredObject)) {
            stack.pop();
        }
        StructuredObject macro = (StructuredObject) stack.pop();
        Symbol s = (Symbol) stack.pop();
        macro.value = value;
        try {
            store.addMacroValue(s, macro.name, macro.values, macro.value.value);
        } catch (MibException e) {
            throw new ModuleException(String.format("mib storage exception: %s", e.getMessage()), parser.getInputStream().getSourceName(), ctx.start);
        }
    }

    @Override
    public void exitMacroAssignement(MacroAssignementContext ctx) {
    }

    @Override
    public void enterTextualConventionAssignement(TextualConventionAssignementContext ctx) {
        stack.push(new TextualConvention());
    }
    @Override
    public void exitTextualConventionAssignement(TextualConventionAssignementContext ctx) {
        while (! (stack.peek() instanceof TextualConvention)) {
            stack.pop();
        }
        TextualConvention tc = (TextualConvention) stack.pop();
        Symbol s = (Symbol) stack.pop();
        store.addTextualConvention(s, tc.values);
    }

    @Override
    public void exitTypeAssignment(TypeAssignmentContext ctx) {
        TypeDescription td = (TypeDescription) stack.pop();
        DeclaredType<?> type = td.resolve();
        Symbol s = (Symbol) stack.pop();

        if (type != null) {
            store.addType(s, type);
        }
    }

    @Override
    public void exitValueAssignment(ValueAssignmentContext ctx) {
        Object value;
        if (stack.peek() instanceof ValueType) {
            @SuppressWarnings("rawtypes")
            ValueType vt = (ValueType) stack.pop();
            value = vt.value;
        } else {
            value = ctx.value() == null ? null : ctx.value().getText();
        }
        TypeDescription td = (TypeDescription) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addValue(s, td.resolve(), value);
        } catch (MibException e) {
            throw new ModuleException(String.format("mib storage exception: %s", e.getMessage()), parser.getInputStream().getSourceName(), ctx.start);
        }
    }

    /****************************************
     * Manage values and push them on stack
     ***************************************/

    @Override
    public void enterObjIdComponentsList(ObjIdComponentsListContext ctx) {
        OidPath oidParts = ctx.objIdComponents().stream().map( i-> {
            OidComponent oidc = new OidComponent();
            if( i.IDENTIFIER() != null) {
                String name = i.IDENTIFIER().getText();
                if (importedFrom.containsKey(name)) {
                    oidc.symbol = new Symbol(importedFrom.get(name), name);
                } else {
                    oidc.symbol = new Symbol(currentModule, name);
                }
            }
            if ( i.NUMBER() != null) {
                oidc.value = Integer.parseInt(i.NUMBER().getText());
            }
            return oidc;
        })
                .collect(OidPath::new, OidPath::add,
                        OidPath::addAll);
        stack.push(new OidType(oidParts));
    }

    @Override
    public void enterBooleanValue(BooleanValueContext ctx) {
        BooleanValue v = new BooleanValue();
        if (ctx.TRUE_LITERAL() != null || ctx.TRUE_SMALL_LITERAL() != null) {
            v.value = true;
        } else {
            v.value = false;
        }
        stack.push(v);
    }

    @Override
    public void enterIntegerValue(IntegerValueContext ctx) {
        Number v = null;
        try {
            if (ctx.signedNumber() != null) {
                v = new BigInteger(ctx.signedNumber().getText());
            } else if (ctx.hexaNumber() != null) {
                String hexanumber = ctx.hexaNumber().HEXANUMBER().getText();
                hexanumber = hexanumber.substring(1, hexanumber.length() - 2);
                if (! hexanumber.isEmpty()) {
                    v = new BigInteger(hexanumber, 16);
                } else {
                    v = 0;
                }
            } else if (ctx.binaryNumber() != null) {
                String binarynumber = ctx.binaryNumber().BINARYNUMBER().getText();
                binarynumber = binarynumber.substring(1, binarynumber.length() - 2);
                if (! binarynumber.isEmpty()) {
                    v = new BigInteger(binarynumber, 2);
                } else {
                    v = 0;
                }
            }
        } catch (Exception e) {
            throw new ModuleException("Invalid number " + ctx.getText(), parser.getInputStream().getSourceName(), ctx.start);
        }
        stack.push(new IntegerValue(v));

    }

    @Override
    public void enterStringValue(StringValueContext ctx) {
        String cstring = ctx.CSTRING().getText();
        cstring = cstring.substring(1, cstring.length() - 1);
        StringValue v = new StringValue(cstring);
        stack.push(v);
    }

    /****************************************
     * Manage complex attributes and push them on stack
     ***************************************/

    @Override
    public void exitComplexAttribut(ComplexAttributContext ctx) {
        String name = ctx.name.getText();
        Object value = null;

        if (ctx.IDENTIFIER() != null) {
            value = resolveSymbol(ctx.IDENTIFIER().getText());
        } else if (ctx.objects() != null) {
            List<ValueType<?>> objects = new ArrayList<>();
            while( (stack.peek() instanceof ValueType)) {
                ValueType<?> vt = (ValueType<?>) stack.pop();
                objects.add(vt);
            }
            value = objects;
        } else if (ctx.groups() != null) {
            value = ctx.groups().IDENTIFIER().stream().map( i -> i.getText()).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.variables() != null) {
            value = ctx.variables().IDENTIFIER().stream().map( i -> i.getText()).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.notifications() != null) {
            value = ctx.notifications().IDENTIFIER().stream().map( i -> i.getText()).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.augments() != null) {
            value = ctx.augments().IDENTIFIER().stream().map( i -> i.getText()).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.index() != null) {
            List<String> types = new ArrayList<>();
            while (stack.peek() instanceof TypeDescription) {
                TypeDescription td = (TypeDescription) stack.pop();
                if (td.typeDescription != null) {
                    types.add(td.typeDescription.toString());
                }
            }
            value = types;
        } else if (stack.peek() instanceof ValueType) {
            ValueType<?> vt = (ValueType<?>)stack.pop();
            value = vt.value;
        } else if (stack.peek() instanceof TypeDescription) {
            value = ((TypeDescription)stack.pop()).resolve();
        }

        while( ! (stack.peek() instanceof MappedObject)) {
            stack.pop();
        }

        MappedObject co = (MappedObject) stack.peek();
        if ("DESCRIPTION".equals(name)) {
            value = "Some description";
        } else if ("CONTACT-INFO".equals(name)) {
            value = "Some description";
        }
        co.values.put(name.intern(), value);
    }

    /****************************************
     * Manage type
     ***************************************/

    @Override
    public void enterType(TypeContext ctx) {
        TypeDescription td = new TypeDescription();
        if (ctx.builtinType() != null) {
            switch(ctx.builtinType().getChild(ParserRuleContext.class, 0).getRuleIndex()) {
            case ASNParser.RULE_integerType:
                td.type = BuiltinType.integerType;
                break;
            case ASNParser.RULE_octetStringType:
                td.type = BuiltinType.octetStringType;
                break;
            case ASNParser.RULE_bitStringType:
                td.type = BuiltinType.bitStringType;
                break;
            case ASNParser.RULE_choiceType:
                td.type = BuiltinType.choiceType;
                break;
            case ASNParser.RULE_enumeratedType:
                td.type = BuiltinType.enumeratedType;
                break;
            case ASNParser.RULE_sequenceType:
                td.type = BuiltinType.sequenceType;
                break;
            case ASNParser.RULE_sequenceOfType:
                td.type = BuiltinType.sequenceOfType;
                break;
            case ASNParser.RULE_setType:
                td.type = BuiltinType.setType;
                break;
            case ASNParser.RULE_setOfType:
                td.type = BuiltinType.setOfType;
                break;
            case ASNParser.RULE_objectidentifiertype:
                td.type = BuiltinType.objectidentifiertype;
                break;
            case ASNParser.RULE_objectClassFieldType:
                td.type = BuiltinType.objectClassFieldType;
                break;
            case ASNParser.RULE_nullType:
                td.type = BuiltinType.nullType;
                break;
            case ASNParser.RULE_bitsType:
                td.type = BuiltinType.bitsType;
                break;
            default:
                throw new ModuleException("Unsupported type", parser.getInputStream().getSourceName(), ctx.start);
            }
        } else if (ctx.referencedType() != null) {
            td.type = BuiltinType.referencedType;
            td.typeDescription = ctx.referencedType();
        }
        stack.push(td);
    }

    @Override
    public void exitType(TypeContext ctx) {
        while ( ! (stack.peek() instanceof TypeDescription)) {
            stack.pop();
        }
    }

    @Override
    public void enterSequenceType(SequenceTypeContext ctx) {
        TypeDescription td = (TypeDescription) stack.peek();
        Map<Symbol, DeclaredType<?>> content = new LinkedHashMap<>();
        td.type = BuiltinType.sequenceType;
        ctx.namedType().forEach( i -> {
            content.put(resolveSymbol(i.IDENTIFIER().getText()), null);
        });
        td.typeDescription = content;
        stack.push("SEQUENCE");
    }

    @Override
    public void exitSequenceType(SequenceTypeContext ctx) {
        List<TypeDescription> nt = new ArrayList<>();
        while ( ! ("SEQUENCE".equals(stack.peek()))) {
            nt.add((TypeDescription)stack.pop());
        }
        stack.pop();
        AtomicInteger i = new AtomicInteger(nt.size() - 1);
        TypeDescription td = (TypeDescription) stack.peek();

        @SuppressWarnings("unchecked")
        Map<Symbol, DeclaredType<?>> content = (Map<Symbol, DeclaredType<?>>) td.typeDescription;
        content.keySet().forEach( name -> {
            content.put(name, nt.get(i.getAndDecrement()).resolve());
        });
    }

    @Override
    public void exitSequenceOfType(SequenceOfTypeContext ctx) {
        TypeDescription seqtd = (TypeDescription) stack.pop();
        TypeDescription td = (TypeDescription) stack.peek();
        td.typeDescription = seqtd.resolve();
    }

    @Override
    public void enterChoiceType(ChoiceTypeContext ctx) {
        TypeDescription td = (TypeDescription) stack.peek();
        Map<String, DeclaredType<?>> content = new LinkedHashMap<>();
        td.type = BuiltinType.choiceType;
        ctx.namedType().forEach( i -> {
            content.put(i.IDENTIFIER().getText(), null);
        });
        td.typeDescription = content;
        stack.push("CHOICE");
    }

    @Override
    public void exitChoiceType(ChoiceTypeContext ctx) {
        List<TypeDescription> nt = new ArrayList<>();
        while ( ! ("CHOICE".equals(stack.peek()))) {
            nt.add((TypeDescription)stack.pop());
        }
        stack.pop();
        int i = nt.size() - 1;
        TypeDescription td = (TypeDescription) stack.peek();
        @SuppressWarnings("unchecked")
        Map<String, DeclaredType<?>> content = (Map<String, DeclaredType<?>>) td.typeDescription;
        content.keySet().forEach( name -> {
            content.put(name, nt.get(i).resolve());
        });
    }

    @Override
    public void enterIntegerType(IntegerTypeContext ctx) {
        TypeDescription td = (TypeDescription) stack.peek();
        if (ctx.namedNumberList() != null) {
            Map<Number, String> names = new HashMap<>();
            ctx.namedNumberList().namedNumber().forEach( i -> {
                Number value = new BigInteger(i.signedNumber().getText());
                String name = i.name.getText();
                names.put(value, name);
            });
            td.names = names;
        }
    }

    @Override
    public void enterBitsType(BitsTypeContext ctx) {
        TypeDescription td = (TypeDescription) stack.peek();
        Map<String, Integer> bits;
        if (ctx.bitsEnumeration() != null && ctx.bitsEnumeration().bitDescription() != null) {
            List<BitDescriptionContext> descriptions = ctx.bitsEnumeration().bitDescription();
            bits = new LinkedHashMap<>(descriptions.size());
            IntStream.range(0, descriptions.size()).forEach( i-> {
                bits.put(descriptions.get(i).IDENTIFIER().getText(), Integer.parseUnsignedInt(descriptions.get(i).NUMBER().getText()));
            });
        } else {
            bits = Collections.emptyMap();
        }
        td.typeDescription = bits;
    }

    @Override
    public void enterReferencedType(ReferencedTypeContext ctx) {
        TypeDescription td = (TypeDescription) stack.peek();
        td.typeDescription = ctx.getText();
        if (ctx.namedNumberList() != null) {
            Map<Number, String> names = new HashMap<>();
            ctx.namedNumberList().namedNumber().forEach( i -> {
                Number value = new BigInteger(i.signedNumber().getText());
                String name = i.name.getText();
                names.put(value, name);
            });
            td.names = names;
        }
    }

}
