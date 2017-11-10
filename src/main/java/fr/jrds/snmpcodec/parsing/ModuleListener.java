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
import org.antlr.v4.runtime.misc.ParseCancellationException;

import fr.jrds.snmpcodec.MibException;
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
import fr.jrds.snmpcodec.parsing.ASNParser.EnterpriseAttributeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.IntegerTypeContext;
import fr.jrds.snmpcodec.parsing.ASNParser.IntegerValueContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleComplianceAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleDefinitionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleIdentityAssignementContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleRevisionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleRevisionsContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ObjIdComponentsListContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ObjectIdentifierValueContext;
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
import fr.jrds.snmpcodec.parsing.MibObject.MappedObject;
import fr.jrds.snmpcodec.parsing.MibObject.ModuleIdentityObject;
import fr.jrds.snmpcodec.parsing.MibObject.ObjectTypeObject;
import fr.jrds.snmpcodec.parsing.MibObject.OtherMacroObject;
import fr.jrds.snmpcodec.parsing.MibObject.Revision;
import fr.jrds.snmpcodec.parsing.MibObject.TextualConventionObject;
import fr.jrds.snmpcodec.parsing.MibObject.TrapTypeObject;
import fr.jrds.snmpcodec.parsing.ValueType.OidValue;
import fr.jrds.snmpcodec.parsing.ValueType.StringValue;
import fr.jrds.snmpcodec.smi.Constraint;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

public class ModuleListener extends ASNBaseListener {

    Parser parser;
    boolean firstError = true;

    private final Deque<Object> stack = new ArrayDeque<>();
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final Map<String, String> importedFrom = new HashMap<>();

    private String currentModule = null;

    final MibLoader store;

    ModuleListener(MibLoader store) {
        this.store = store;
    }

    Symbol resolveSymbol(String name) {
        Symbol newSymbol;
        if (importedFrom.containsKey(name)) {
            newSymbol = new Symbol(importedFrom.get(name), name);
        } else {
            newSymbol = new Symbol(currentModule, name);
        }
        symbols.put(name, newSymbol);
        return newSymbol;
    }

    private Number fitNumber(BigInteger v) {
        Number finalV = null;
        int bitLength = v.bitLength();
        if (bitLength < 7) {
            finalV = new Byte((byte) v.intValue());
        } else if (bitLength < 15) {
            finalV = new Short((short)v.intValue());
        } else if (bitLength < 31) {
            finalV = new Integer((int)v.intValue());
        } else if (bitLength < 63) {
            finalV = new Long((long)v.longValue());
        } else {
            finalV = v;
        }
        return finalV;
    }

    @Override
    public void enterModuleDefinition(ModuleDefinitionContext ctx) {
        currentModule = ctx.IDENTIFIER().getText();
        symbols.clear();

        //The root symbols are often forgotten
        Symbol ccitt = new Symbol("ccitt");
        Symbol iso = new Symbol("iso");
        Symbol joint = new Symbol("joint-iso-ccitt");
        symbols.put(ccitt.name, ccitt);
        symbols.put(iso.name, iso);
        symbols.put(joint.name, joint);

        importedFrom.clear();
        try {
            store.newModule(currentModule);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void enterSymbolsFromModule(SymbolsFromModuleContext ctx) {
        ctx.symbolList().symbol().stream()
        .forEach( i->  {
            String name = i.getText();
            String module = ctx.globalModuleReference().getText();
            importedFrom.put(name, module);
            symbols.put(name, new Symbol(module, name));
        });
    }

    /****************************************
     * Manage assignments and push them on stack
     * assignments: objectTypeAssignement, valueAssignment, typeAssignment, textualConventionAssignement, macroAssignement
     ***************************************/

    @Override
    public void enterAssignment(AssignmentContext ctx) {
        stack.clear();
        stack.push(resolveSymbol(ctx.identifier.getText()));
    }

    @Override
    public void enterComplexAssignement(ComplexAssignementContext ctx) {
        stack.push(new OtherMacroObject(ctx.macroName().getText()));
    }

    @Override
    public void exitComplexAssignement(ComplexAssignementContext ctx) {
        OidValue value = (OidValue) stack.pop();
        OtherMacroObject macro = (OtherMacroObject) stack.pop();
        Symbol s = (Symbol) stack.pop();
        macro.value = value;
        try {
            store.addMacroValue(s, macro.name, macro.values, macro.value.value);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void enterTrapTypeAssignement(TrapTypeAssignementContext ctx) {
        stack.push(new TrapTypeObject());
    }

    @Override
    public void exitTrapTypeAssignement(TrapTypeAssignementContext ctx) {
        @SuppressWarnings("unchecked")
        ValueType<Number> value = (ValueType<Number>) stack.pop();
        TrapTypeObject macro = (TrapTypeObject) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            if (macro.enterprise != null) {
                store.addTrapType(s, macro.enterprise, macro.values, value.value);
            }
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void enterObjectTypeAssignement(ObjectTypeAssignementContext ctx) {
        stack.push(new ObjectTypeObject());
    }

    @Override
    public void exitObjectTypeAssignement(ObjectTypeAssignementContext ctx) {
        OidValue vt = (OidValue) stack.pop();
        ObjectTypeObject macro = (ObjectTypeObject) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addObjectType(s, macro.values, vt.value);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void enterTextualConventionAssignement(TextualConventionAssignementContext ctx) {
        stack.push(new TextualConventionObject());
    }

    @Override
    public void exitTextualConventionAssignement(TextualConventionAssignementContext ctx) {
        TextualConventionObject tc = (TextualConventionObject) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addTextualConvention(s, tc.values);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void enterModuleIdentityAssignement(ModuleIdentityAssignementContext ctx) {
        stack.push(new ModuleIdentityObject());
    }

    @Override
    public void exitModuleIdentityAssignement(ModuleIdentityAssignementContext ctx) {
        OidValue vt = (OidValue) stack.pop();
        Object revisions = stack.pop();
        while ( ! (stack.peek() instanceof ModuleIdentityObject)) {
            stack.pop();
        }
        ModuleIdentityObject mi = (ModuleIdentityObject) stack.pop();;
        Symbol s = (Symbol) stack.pop();
        mi.values.put("revisions", revisions);
        try {
            store.addModuleIdentity(s, mi.values, vt.value);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void exitTypeAssignment(TypeAssignmentContext ctx) {
        TypeDescription td = (TypeDescription) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            store.addType(s, td.getSyntax(this));
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void exitValueAssignment(ValueAssignmentContext ctx) {
        ValueType<?> vt = (ValueType<?>) stack.pop();
        TypeDescription td = (TypeDescription) stack.pop();
        Symbol s = (Symbol) stack.pop();
        try {
            if (vt.value instanceof OidPath) {
                OidPath path = (OidPath) vt.value;
                store.addValue(s, td.getSyntax(this), path);
            }
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    /****************************************
     * Manage values and push them on stack
     ***************************************/

    @Override
    public void exitObjectIdentifierValue(ObjectIdentifierValueContext ctx) {
        OidValue stackval = (OidValue) stack.peek();
        OidPath oidParts = stackval.value;
        if (ctx.IDENTIFIER() != null) {
            String name = ctx.IDENTIFIER().getText();
            if (symbols.containsKey(name)) {
                oidParts.root = symbols.get(name);
            } else {
                oidParts.root = new Symbol(currentModule, name);
            }
        }
    }

    @Override
    public void enterObjIdComponentsList(ObjIdComponentsListContext ctx) {
        OidPath oidParts = ctx.objIdComponents().stream().map( i-> {
            String name = null;
            int number;
            if( i.identifier != null) {
                name = i.identifier.getText();
            }
            number = Integer.parseInt(i.NUMBER().getText());
            OidPath.OidComponent oidc = new OidPath.OidComponent(name, number);
            return oidc;
        })
                .collect(OidPath::new, OidPath::add,
                        OidPath::addAll);
        stack.push(new OidValue(oidParts));
    }

    @Override
    public void enterBooleanValue(BooleanValueContext ctx) {
        boolean value;
        if ("true".equalsIgnoreCase(ctx.getText())) {
            value = true;
        } else {
            value = false;
        }
        ValueType.BooleanValue v = new ValueType.BooleanValue(value);
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
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
        stack.push(new ValueType.IntegerValue(fitNumber(v)));

    }

    @Override
    public void enterStringValue(StringValueContext ctx) {
        try {
            if (ctx.CSTRING() == null || ctx.CSTRING().getText() == null) {
                Exception e = new NullPointerException();
                parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
            }
            String cstring = ctx.CSTRING().getText();
            cstring = cstring.substring(1, cstring.length() - 1);
            ValueType.StringValue v = new ValueType.StringValue(cstring);
            stack.push(v);
        } catch (Exception e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
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
            value = ((TypeDescription)stack.pop()).getSyntax(this);
        }

        MappedObject co = (MappedObject) stack.peek();
        co.values.put(name.intern(), value);
    }

    @Override
    public void exitEnterpriseAttribute(EnterpriseAttributeContext ctx) {
        Object enterprise;
        if (ctx.IDENTIFIER() != null) {
            enterprise = resolveSymbol(ctx.IDENTIFIER().getText());
        } else if ( ctx.objectIdentifierValue() != null){
            OidValue value = (OidValue) stack.pop();
            enterprise = value.value;
        } else {
            MibException e = new MibException("Invalid trap");
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
            return;
        }
        TrapTypeObject co = (TrapTypeObject) stack.peek();
        co.enterprise = enterprise;
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

    @Override
    public void enterModuleRevisions(ModuleRevisionsContext ctx) {
        stack.push(new ArrayList<Revision>());
    }

    @Override
    public void exitModuleRevision(ModuleRevisionContext ctx) {
        StringValue description = (StringValue)stack.pop();
        StringValue revision = (StringValue)stack.pop();
        @SuppressWarnings("unchecked")
        List<Revision> revisions = (List<Revision>) stack.peek();
        revisions.add(new Revision(description.value, revision.value));
    }

    @Override
    public void enterModuleComplianceAssignement(ModuleComplianceAssignementContext ctx) {
        stack.push(new MappedObject("MODULE-COMPLIANCE"));
    }

    @Override
    public void exitModuleComplianceAssignement(ModuleComplianceAssignementContext ctx) {
        OidValue value = (OidValue) stack.pop();
        while(! (stack.peek() instanceof Symbol)) {
            stack.pop().getClass();
        }
        Symbol s = (Symbol) stack.pop();
        try {
            store.addMacroValue(s, "MODULE-COMPLIANCE", Collections.emptyMap(), value.value);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
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
                td.type = Asn1Type.integerType;
                break;
            case ASNParser.RULE_octetStringType:
                td.type = Asn1Type.octetStringType;
                break;
            case ASNParser.RULE_bitStringType:
                td.type = Asn1Type.bitStringType;
                break;
            case ASNParser.RULE_choiceType:
                td.type = Asn1Type.choiceType;
                break;
            case ASNParser.RULE_sequenceType:
                td.type = Asn1Type.sequenceType;
                break;
            case ASNParser.RULE_sequenceOfType:
                td.type = Asn1Type.sequenceOfType;
                break;
            case ASNParser.RULE_objectIdentifierType:
                td.type = Asn1Type.objectidentifiertype;
                break;
            case ASNParser.RULE_nullType:
                td.type = Asn1Type.nullType;
                break;
            case ASNParser.RULE_bitsType:
                td.type = Asn1Type.bitsType;
                break;
            default:
                throw new ParseCancellationException();
                //throw new ModuleException("Unsupported ASN.1 type", parser.getInputStream().getSourceName(), ctx.start);
            }
        } else if (ctx.referencedType() != null) {
            td.type = Asn1Type.referencedType;
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
        while( stack.peek() instanceof ValueType.IntegerValue) {
            ValueType.IntegerValue val = (ValueType.IntegerValue) stack.pop();
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
        Map<String, Syntax> content = new LinkedHashMap<>();
        td.type = Asn1Type.sequenceType;
        ctx.namedType().forEach( i -> {
            content.put(i.IDENTIFIER().getText(), null);
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
        Map<String, Syntax> content = (Map<String, Syntax>) td.typeDescription;
        content.keySet().forEach( name -> {
            content.put(name, nt.get(i.getAndDecrement()).getSyntax(this));
        });
    }

    @Override
    public void exitSequenceOfType(SequenceOfTypeContext ctx) {
        TypeDescription seqtd = (TypeDescription) stack.pop();
        TypeDescription td = (TypeDescription) stack.peek();
        td.typeDescription = seqtd;
    }

    @Override
    public void enterChoiceType(ChoiceTypeContext ctx) {
        TypeDescription td = (TypeDescription) stack.peek();
        Map<String, Syntax> content = new LinkedHashMap<>();
        td.type = Asn1Type.choiceType;
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
        Map<String, Syntax> content = (Map<String, Syntax>) td.typeDescription;
        content.keySet().forEach( name -> {
            content.put(name, nt.get(i).getSyntax(this));
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
    }

}
