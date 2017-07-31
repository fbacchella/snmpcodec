package fr.jrds.snmpcodec.parsing;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.parsing.ASNParser.AccessContext;
import fr.jrds.snmpcodec.parsing.ASNParser.AssignmentContext;
import fr.jrds.snmpcodec.parsing.ASNParser.BitDescriptionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.BitsTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.BooleanValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ChoiceTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ComplexAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ComplexAttributContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ConstraintContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ElementsContext;
import fr.jrds.snmpcodec.parsing.ASNParser.IntegerTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.IntegerValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleDefinitionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ObjIdComponentsListContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ObjectTypeAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ReferencedTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SequenceOfTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SequenceTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SizeConstraintContext;
import fr.jrds.snmpcodec.parsing.ASNParser.StatusContext;
import fr.jrds.snmpcodec.parsing.ASNParser.StringValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.SymbolsFromModuleContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TextualConventionAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TrapTypeAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TypeAssignmentContext;
import fr.jrds.snmpcodec.parsing.ASNParser.TypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ValueAssignmentContext;
import fr.jrds.snmpcodec.smi.Constraint;
import fr.jrds.snmpcodec.smi.DeclaredType;
import fr.jrds.snmpcodec.smi.Oid.OidComponent;
import fr.jrds.snmpcodec.smi.Oid.OidPath;
import fr.jrds.snmpcodec.smi.SmiType;
import fr.jrds.snmpcodec.smi.Symbol;

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

    static abstract class ValueType<T> {
        final T value;
        ValueType(T value) {
            this.value = value;
        }
        @Override
        public String toString() {
            return value.toString();
        }
    }

    static class OidType extends ValueType<List<OidComponent>> {
        OidType(List<OidComponent> value) {
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
        Constraint constraints = null;

        @Override
        public String toString() {
            return "" + type + (typeDescription != null ? " " + typeDescription : "");
        }
        @SuppressWarnings("unchecked")
        public DeclaredType<?> resolve() {
            switch (type) {
            case referencedType:
                return new DeclaredType.Referenced(resolveSymbol((String) typeDescription), names, constraints);
            case octetStringType:
                return new DeclaredType.Native(SmiType.OctetString, names, constraints);
            case bitStringType:
                return new DeclaredType.Bits((Map<String, Integer>) typeDescription, names, constraints);
            case integerType:
                return new DeclaredType.Native(SmiType.INTEGER, names, constraints);
            case objectidentifiertype:
                return new DeclaredType.Native(SmiType.ObjID, names, constraints);
            case nullType:
                return new DeclaredType.Native(SmiType.Null, names, constraints);
            case sequenceType:
                return new DeclaredType.Sequence((Map<String, DeclaredType<?>>)typeDescription, names);
            case sequenceOfType:
                return new DeclaredType.SequenceOf((DeclaredType<?>) typeDescription, names);
            case choiceType:
                return new DeclaredType.Choice((Map<String, DeclaredType<?>>)typeDescription, names);
            case bitsType:
                return new DeclaredType.Bits((Map<String, Integer>)typeDescription, names, constraints);
            case enumeratedType:
            case objectClassFieldType:
            case setType:
            case setOfType:
                System.out.format("unmanaged type: %s\n", this);
                return new DeclaredType.Native(SmiType.Null, names, constraints);
            default:
                System.out.format("unchecked type: %s\n", this);
                return new DeclaredType.Native(SmiType.Null, names, constraints);
            }
        }
    }

    private final Deque<Object> stack = new ArrayDeque<>();

    Parser parser;

    private final Map<String, Object> objects = new HashMap<>();
    private final Map<String, String> importedFrom = new HashMap<>();
    private final Map<OidType, MibObject> symbols = new HashMap<>();

    private String currentModule = null;

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
    
    private Number fitNumber(BigInteger v) {
        Number finalV = null;
        switch(v.bitCount()) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 7:
            finalV = new Byte((byte) v.intValue());
            break;
        case 8:
        case 15:
            finalV = new Short((short)v.intValue());
            break;
        case 16:
        case 31:
            finalV = new Integer(v.intValue());
            break;
        case 32:
        case 63:
            finalV = new Long(v.longValue());
            break;
        case 64:
        default:
            finalV = v;
        }
        return finalV;
    }

    @Override
    public void enterModuleDefinition(ModuleDefinitionContext ctx) {
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
     * Manage assignments and push them on stack
     * assignments: objectTypeAssignement, valueAssignment, typeAssignment, textualConventionAssignement, macroAssignement
     ***************************************/

    @Override
    public void enterAssignment(AssignmentContext ctx) {
        stack.push(resolveSymbol(ctx.identifier.getText()));
    }

    @Override
    public void enterComplexAssignement(ComplexAssignementContext ctx) {
        stack.push(new StructuredObject<OidPath>(ctx.macroName().getText()));
    }

    @Override
    public void exitComplexAssignement(ComplexAssignementContext ctx) {
        @SuppressWarnings("unchecked")
        ValueType<OidPath> value = (ValueType<OidPath>) stack.pop();
        @SuppressWarnings("unchecked")
        StructuredObject<OidPath> macro = (StructuredObject<OidPath>) stack.pop();
        Symbol s = (Symbol) stack.pop();
        macro.value = value;
        try {
            store.addMacroValue(s, macro.name, macro.values, macro.value.value);
        } catch (MibException e) {
            throw new ModuleException(String.format("mib storage exception: %s", e.getMessage()), parser.getInputStream().getSourceName(), ctx.start);
        }
    }

    @Override
    public void enterTrapTypeAssignement(TrapTypeAssignementContext ctx) {
        stack.push(new StructuredObject<Number>("TRAP_TYPE"));
    }

    @Override
    public void exitTrapTypeAssignement(TrapTypeAssignementContext ctx) {
        @SuppressWarnings("unchecked")
        ValueType<Number> value = (ValueType<Number>) stack.pop();
        @SuppressWarnings("unchecked")
        StructuredObject<Number> macro = (StructuredObject<Number>) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addTrapType(s, macro.name, macro.values, value.value);
        } catch (MibException e) {
            throw new ModuleException(String.format("mib storage exception: %s", e.getMessage()), parser.getInputStream().getSourceName(), ctx.start);
        }
    }

    @Override
    public void enterObjectTypeAssignement(ObjectTypeAssignementContext ctx) {
        stack.push(new StructuredObject<OidPath>("OBJECT_TYPE"));
    }

    @Override
    public void exitObjectTypeAssignement(ObjectTypeAssignementContext ctx) {
        @SuppressWarnings("unchecked")
        ValueType<OidPath> vt = (ValueType<OidPath>) stack.pop();
        @SuppressWarnings("unchecked")
        StructuredObject<OidPath> macro = (StructuredObject<OidPath>) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addObjectType(s, macro.values, vt.value);
        } catch (MibException e) {
            throw new ModuleException(String.format("mib storage exception: %s", e.getMessage()), parser.getInputStream().getSourceName(), ctx.start);
        }
    }

    @Override
    public void enterTextualConventionAssignement(TextualConventionAssignementContext ctx) {
        stack.push(new TextualConvention());
    }

    @Override
    public void exitTextualConventionAssignement(TextualConventionAssignementContext ctx) {
        TextualConvention tc = (TextualConvention) stack.pop();
        Symbol s = (Symbol) stack.pop();
        DeclaredType<?> dt = (DeclaredType<?>) tc.values.get("SYNTAX");
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
        ValueType<?> vt = (ValueType<?>) stack.pop();
        TypeDescription td = (TypeDescription) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addValue(s, td.resolve(), vt.value);
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
        boolean value;
        if (ctx.TRUE_LITERAL() != null || ctx.TRUE_SMALL_LITERAL() != null) {
            value = true;
        } else {
            value = false;
        }
        BooleanValue v = new BooleanValue(value);
        stack.push(v);
    }

    @Override
    public void enterIntegerValue(IntegerValueContext ctx) {
        BigInteger v = null;
        try {
            if (ctx.signedNumber() != null) {
                v = new BigInteger(ctx.signedNumber().getText());
            } else if (ctx.hexaNumber() != null) {
                String hexanumber = ctx.hexaNumber().HEXANUMBER().getText();
                hexanumber = hexanumber.substring(1, hexanumber.length() - 2);
                if (! hexanumber.isEmpty()) {
                    v = new BigInteger(hexanumber, 16);
                } else {
                    v = BigInteger.valueOf(0);
                }
            } else if (ctx.binaryNumber() != null) {
                String binarynumber = ctx.binaryNumber().BINARYNUMBER().getText();
                binarynumber = binarynumber.substring(1, binarynumber.length() - 2);
                if (! binarynumber.isEmpty()) {
                    v = new BigInteger(binarynumber, 2);
                } else {
                    v = BigInteger.valueOf(0);
                }
            }
        } catch (Exception e) {
            throw new ModuleException("Invalid number " + ctx.getText(), parser.getInputStream().getSourceName(), ctx.start);
        }
        stack.push(new IntegerValue(fitNumber(v)));

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
        if (ctx.name == null) {
            return;
        }
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
            LinkedList<Symbol> types = new LinkedList<>();
            while (stack.peek() instanceof TypeDescription) {
                TypeDescription td = (TypeDescription) stack.pop();
                if (td.typeDescription != null) {
                    types.addFirst(resolveSymbol(td.typeDescription.toString()));
                }
            }
            value = new ArrayList<Symbol>(types);
        } else if (stack.peek() instanceof ValueType) {
            ValueType<?> vt = (ValueType<?>)stack.pop();
            value = vt.value;
        } else if (stack.peek() instanceof TypeDescription) {
            value = ((TypeDescription)stack.pop()).resolve();
        }

        MappedObject co = (MappedObject) stack.peek();
        if ("DESCRIPTION".equals(name)) {
            value = "Some description";
        } else if ("CONTACT-INFO".equals(name)) {
            value = "Some description";
        }
        co.values.put(name.intern(), value);
    }

    @Override
    public void exitAccess(AccessContext ctx) {
        String name = ctx.name.getText();
        String value = ctx.IDENTIFIER().getText().intern();
        MappedObject co = (MappedObject) stack.peek();
        co.values.put(name.intern(), value);
    }

    @Override
    public void exitStatus(StatusContext ctx) {
        String name = ctx.name.getText();
        String value = ctx.IDENTIFIER().getText().intern();
        MappedObject co = (MappedObject) stack.peek();
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
            case ASNParser.RULE_objectidentifiertype:
                td.type = BuiltinType.objectidentifiertype;
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
        Constraint constrains = null;
        if (stack.peek() instanceof Constraint) {
            constrains = (Constraint) stack.pop();
        }
        TypeDescription td = (TypeDescription) stack.peek();
        td.constraints = constrains;
    }

    @Override
    public void enterConstraint(ConstraintContext ctx) {
        stack.push(new Constraint(false));
    }

    @Override
    public void exitConstraint(ConstraintContext ctx) {
        Constraint constrains = (Constraint) stack.peek();
        constrains.finish();
    }

    @Override
    public void enterSizeConstraint(SizeConstraintContext ctx) {
        stack.push(new Constraint(true));
    }

    @Override
    public void exitSizeConstraint(SizeConstraintContext ctx) {
        Constraint constrains = (Constraint) stack.peek();
        constrains.finish();
    }

    @Override
    public void exitElements(ElementsContext ctx) {
        List<Number> values = new ArrayList<>(2);
        while( stack.peek() instanceof IntegerValue) {
            IntegerValue val = (IntegerValue) stack.pop();
            values.add(val.value);
        }
        Constraint.ConstraintElement c;
        if (values.size() == 1) {
            c = new Constraint.ConstraintElement(values.get(0));
        } else {
            c = new Constraint.ConstraintElement(values.get(1), values.get(0));
        }
        Constraint constrains = (Constraint) stack.peek();
        constrains.add(c);
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
    }

    @Override
    public void exitSequenceType(SequenceTypeContext ctx) {
        List<TypeDescription> nt = new ArrayList<>();
        int namedTypeCount = ctx.namedType().size();
        for (int i = 0; i < namedTypeCount; i++ ) {
            nt.add((TypeDescription)stack.pop());
        }
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
                BigInteger value = new BigInteger(i.signedNumber().getText());
                String name = i.name.getText();
                names.put(fitNumber(value), name);
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
                Number value = new Integer(i.signedNumber().getText());
                String name = i.name.getText();
                names.put(value, name);
            });
            td.names = names;
        }
    }

}
