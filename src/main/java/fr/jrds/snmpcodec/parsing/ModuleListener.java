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
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;

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
import fr.jrds.snmpcodec.parsing.ValueType.IntegerValue;
import fr.jrds.snmpcodec.smi.Constraint;
import fr.jrds.snmpcodec.smi.SmiType;
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
        Number finalV;
        int bitLength = v.bitLength();
        if (bitLength < 7) {
            finalV = (byte) v.intValue();
        } else if (bitLength < 15) {
            finalV = (short) v.intValue();
        } else if (bitLength < 31) {
            finalV = v.intValue();
        } else if (bitLength < 63) {
            finalV = v.longValue();
        } else {
            finalV = v;
        }
        return finalV;
    }

    private <T> T checkedStackOption(ParserRuleContext ctx, Class<T> expected, Supplier<T> stackOp) {
        if (stack.isEmpty()) {
            RecognitionException ex = new RecognitionException("Empty stack", parser, parser.getInputStream(), ctx);
            parser.notifyErrorListeners(ctx.start, ex.getMessage(), ex);
            throw new IllegalStateException("Inconsistent stack");
        } else if (! expected.isAssignableFrom(stack.peek().getClass())) {
            stack.clear();
            RecognitionException ex = new RecognitionException("Inconsistent parsing stack", parser, parser.getInputStream(), ctx);
            parser.notifyErrorListeners(ctx.start, ex.getMessage(), ex);
            throw new IllegalStateException("Inconsistent stack");
        } else {
            return stackOp.get();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T checkedPop(ParserRuleContext ctx, Class<T> expected) {
           return checkedStackOption(ctx, expected, () -> (T) stack.pop());
    }

    @SuppressWarnings("unchecked")
    private <T> T checkedPeek(ParserRuleContext ctx, Class<T> expected) {
        return checkedStackOption(ctx, expected, () -> (T) stack.peek());
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
        ctx.symbolList().symbol()
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
        OidValue value = checkedPop(ctx, OidValue.class);
        OtherMacroObject macro = checkedPop(ctx, OtherMacroObject.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        if (value == null || macro == null || s == null) {
            return;
        }
        macro.value = value;
        try {
            store.addMacroValue(s, macro.value.value);
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
        IntegerValue value = checkedPop(ctx, IntegerValue.class);
        TrapTypeObject macro = checkedPop(ctx, TrapTypeObject.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        if (value == null || macro == null || s == null) {
            return;
        }
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
        OidValue vt = checkedPop(ctx, OidValue.class);
        ObjectTypeObject macro = checkedPop(ctx, ObjectTypeObject.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        if (vt == null || macro == null || s == null) {
            return;
        }
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
        TextualConventionObject tc = checkedPop(ctx, TextualConventionObject.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        if (tc == null || s == null) {
            return;
        }
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
        OidValue vt = checkedPop(ctx, OidValue.class);
        Object revisions = checkedPop(ctx, Object.class);
        if (vt == null || revisions == null) {
            return;
        }
        while (! (stack.peek() instanceof ModuleIdentityObject) && ! stack.isEmpty()) {
            stack.pop();
        }
        ModuleIdentityObject mi = checkedPop(ctx, ModuleIdentityObject.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        mi.values.put("revisions", revisions);
        try {
            store.addModuleIdentity(s, vt.value);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void exitTypeAssignment(TypeAssignmentContext ctx) {
        TypeDescription td = checkedPop(ctx, TypeDescription.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        if (td == null || s == null) {
            return;
        }
        try {
            Syntax sy;
            if ("SNMPv2-SMI".equals(s.module)) {
                switch (s.name) {
                case "IpAddress":
                    sy = SmiType.IpAddr;
                    break;
                case "Counter32":
                    sy = SmiType.Counter32;
                    break;
                case "Gauge32":
                    sy = SmiType.Gauge32;
                    break;
                case "Unsigned32":
                    sy = SmiType.Unsigned32;
                    break;
                case "TimeTicks":
                    sy = SmiType.TimeTicks;
                    break;
                case "Opaque":
                    sy = SmiType.Opaque;
                    break;
                case "Counter64":
                    sy = SmiType.Counter64;
                    break;
                default:
                    sy = td.getSyntax(this);
                }
            } else {
                sy = td.getSyntax(this);
            }
            store.addType(s, sy);
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void exitValueAssignment(ValueAssignmentContext ctx) {
        ValueType<?> vt = checkedPop(ctx, ValueType.class);
        // Removed the unused TypeDescription
        checkedPop(ctx, Object.class);
        Symbol s = checkedPop(ctx, Symbol.class);
        if (vt == null || s == null) {
            return;
        }
        try {
            if (vt.value instanceof OidPath) {
                OidPath path = (OidPath) vt.value;
                store.addValue(s, path);
            }
        } catch (MibException e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }
    }

    @Override
    public void exitAssignmentList(ASNParser.AssignmentListContext ctx) {
        stack.clear();
    }

    /****************************************
     * Manage values and push them on stack
     ***************************************/

    @Override
    public void exitObjectIdentifierValue(ObjectIdentifierValueContext ctx) {
        OidValue stackval = checkedPeek(ctx, OidValue.class);
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
            return new OidPath.OidComponent(name, number);
        })
                .collect(OidPath::new, OidPath::add,
                        OidPath::addAll);
        stack.push(new OidValue(oidParts));
    }

    @Override
    public void enterBooleanValue(BooleanValueContext ctx) {
        boolean value = "true".equalsIgnoreCase(ctx.getText());
        ValueType.BooleanValue v = new ValueType.BooleanValue(value);
        stack.push(v);
    }

    @Override
    public void enterIntegerValue(IntegerValueContext ctx) {
        BigInteger v;
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
            } else {
                //Binary number case
                String binarynumber = ctx.binaryNumber().BINARYNUMBER().getText();
                binarynumber = binarynumber.substring(1, binarynumber.length() - 2);
                if (! binarynumber.isEmpty()) {
                    v = new BigInteger(binarynumber, 2);
                } else {
                    v = BigInteger.valueOf(0);
                }
            }
            stack.push(new ValueType.IntegerValue(fitNumber(v)));
        } catch (Exception e) {
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
        }

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
            while (stack.peek() instanceof ValueType) {
                ValueType<?> vt = checkedPop(ctx, ValueType.class);
                objects.add(vt);
            }
            value = objects;
        } else if (ctx.groups() != null) {
            value = ctx.groups().IDENTIFIER().stream().map(TerminalNode::getText).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.variables() != null) {
            value = ctx.variables().IDENTIFIER().stream().map(TerminalNode::getText).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.notifications() != null) {
            value = ctx.notifications().IDENTIFIER().stream().map(TerminalNode::getText).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        } else if (ctx.augments() != null) {
            value = resolveSymbol(ctx.augments().IDENTIFIER().getText());
        } else if (ctx.index() != null) {
            LinkedList<Symbol> types = new LinkedList<>();
            while (stack.peek() instanceof TypeDescription) {
                TypeDescription td = checkedPop(ctx, TypeDescription.class);
                if (td.typeDescription != null) {
                    types.addFirst(resolveSymbol(td.typeDescription.toString()));
                }
            }
            value = new ArrayList<>(types);
        } else if (stack.peek() instanceof ValueType) {
            ValueType<?> vt = checkedPop(ctx, ValueType.class);
            value = vt.value;
        } else if (stack.peek() instanceof TypeDescription) {
            value = checkedPop(ctx, TypeDescription.class).getSyntax(this);
        }

        MappedObject co = checkedPeek(ctx, MappedObject.class);
        co.values.put(name.intern(), value);
    }

    @Override
    public void exitEnterpriseAttribute(EnterpriseAttributeContext ctx) {
        Object enterprise;
        if (ctx.IDENTIFIER() != null) {
            enterprise = resolveSymbol(ctx.IDENTIFIER().getText());
        } else if ( ctx.objectIdentifierValue() != null){
            OidValue value = checkedPop(ctx, OidValue.class);
            enterprise = value.value;
        } else {
            MibException e = new MibException("Invalid trap");
            parser.notifyErrorListeners(ctx.start, e.getMessage(), new WrappedException(e, parser, parser.getInputStream(), ctx));
            return;
        }
        TrapTypeObject co = checkedPeek(ctx, TrapTypeObject.class);
        co.enterprise = enterprise;
    }

    @Override
    public void exitAccess(AccessContext ctx) {
        String name = ctx.name.getText();
        String value = ctx.IDENTIFIER().getText().intern();
        MappedObject co = checkedPeek(ctx, MappedObject.class);
        co.values.put(name.intern(), value);
    }

    @Override
    public void exitStatus(StatusContext ctx) {
        String name = ctx.name.getText();
        String value = ctx.IDENTIFIER().getText().intern();
        MappedObject co = checkedPeek(ctx, MappedObject.class);
        co.values.put(name.intern(), value);
    }

    @Override
    public void enterModuleRevisions(ModuleRevisionsContext ctx) {
        stack.push(new ArrayList<Revision>());
    }

    @Override
    public void exitModuleRevision(ModuleRevisionContext ctx) {
        StringValue description = checkedPop(ctx, StringValue.class);
        StringValue revision= checkedPop(ctx, StringValue.class);
        if (description == null || revision == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Revision> revisions = checkedPeek(ctx, List.class);
        revisions.add(new Revision(description.value, revision.value));
    }

    @Override
    public void enterModuleComplianceAssignement(ModuleComplianceAssignementContext ctx) {
        stack.push(new MappedObject("MODULE-COMPLIANCE"));
    }

    @Override
    public void exitModuleComplianceAssignement(ModuleComplianceAssignementContext ctx) {
        OidValue value = checkedPop(ctx, OidValue.class);
        if (value == null) {
            return;
        }
        while(! (stack.peek() instanceof Symbol) && ! stack.isEmpty()) {
            stack.pop();
        }
        Symbol s = checkedPop(ctx, Symbol.class);
        if (s == null) {
            return;
        }
        try {
            store.addMacroValue(s, value.value);
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
            }
        } else if (ctx.referencedType() != null) {
            td.type = Asn1Type.referencedType;
            td.typeDescription = ctx.referencedType();
        }
        stack.push(td);
    }

    @Override
    public void exitType(TypeContext ctx) {
        if (stack.peek() instanceof Constraint) {
            Constraint constrains = checkedPop(ctx, Constraint.class);
            TypeDescription td = checkedPeek(ctx, TypeDescription.class);
            td.constraints = constrains;
        }
    }

    @Override
    public void enterConstraint(ConstraintContext ctx) {
        stack.push(new Constraint(false));
    }

    @Override
    public void exitConstraint(ConstraintContext ctx) {
        Constraint constrains = checkedPeek(ctx, Constraint.class);
        constrains.finish();
    }

    @Override
    public void enterSizeConstraint(SizeConstraintContext ctx) {
        stack.push(new Constraint(true));
    }

    @Override
    public void exitSizeConstraint(SizeConstraintContext ctx) {
        Constraint constrains = checkedPeek(ctx, Constraint.class);
        constrains.finish();
    }

    @Override
    public void exitElements(ElementsContext ctx) {
        List<Number> values = new ArrayList<>(2);
        while (stack.peek() instanceof IntegerValue) {
            IntegerValue val = checkedPop(ctx, IntegerValue.class);
            values.add(val.value);
        }
        Constraint.ConstraintElement c;
        if (values.size() == 1) {
            c = new Constraint.ConstraintElement(values.get(0));
        } else {
            c = new Constraint.ConstraintElement(values.get(1), values.get(0));
        }
        Constraint constrains = checkedPeek(ctx, Constraint.class);
        constrains.add(c);
    }

    @Override
    public void enterSequenceType(SequenceTypeContext ctx) {
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
        Map<String, Syntax> content = new LinkedHashMap<>();
        td.type = Asn1Type.sequenceType;
        ctx.namedType().forEach( i -> content.put(i.IDENTIFIER().getText(), null));
        td.typeDescription = content;
    }

    @Override
    public void exitSequenceType(SequenceTypeContext ctx) {
        List<TypeDescription> nt = new ArrayList<>();
        int namedTypeCount = ctx.namedType().size();
        for (int i = 0; i < namedTypeCount; i++ ) {
            nt.add(checkedPop(ctx, TypeDescription.class));
        }
        AtomicInteger i = new AtomicInteger(nt.size() - 1);
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);

        @SuppressWarnings("unchecked")
        Map<String, Syntax> content = (Map<String, Syntax>) td.typeDescription;
        content.keySet().forEach( name -> content.put(name, nt.get(i.getAndDecrement()).getSyntax(this)));
    }

    @Override
    public void exitSequenceOfType(SequenceOfTypeContext ctx) {
        TypeDescription seqtd = checkedPop(ctx, TypeDescription.class);
        if (seqtd == null) {
            return;
        }
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
        td.typeDescription = seqtd;
    }

    @Override
    public void enterChoiceType(ChoiceTypeContext ctx) {
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
        Map<String, Syntax> content = new LinkedHashMap<>();
        td.type = Asn1Type.choiceType;
        ctx.namedType().forEach( i -> content.put(i.IDENTIFIER().getText(), null));
        td.typeDescription = content;
        stack.push("CHOICE");
    }

    @Override
    public void exitChoiceType(ChoiceTypeContext ctx) {
        List<TypeDescription> nt = new ArrayList<>();
        while (! ("CHOICE".equals(stack.peek()))) {
            nt.add(checkedPop(ctx, TypeDescription.class));
        }
        stack.pop();
        int i = nt.size() - 1;
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
        @SuppressWarnings("unchecked")
        Map<String, Syntax> content = (Map<String, Syntax>) td.typeDescription;
        content.keySet().forEach( name -> content.put(name, nt.get(i).getSyntax(this)));
    }

    @Override
    public void enterIntegerType(IntegerTypeContext ctx) {
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
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
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
        Map<String, Integer> bits;
        if (ctx.bitsEnumeration() != null && ctx.bitsEnumeration().bitDescription() != null) {
            List<BitDescriptionContext> descriptions = ctx.bitsEnumeration().bitDescription();
            bits = new LinkedHashMap<>(descriptions.size());
            IntStream.range(0, descriptions.size()).forEach(i-> bits.put(descriptions.get(i).IDENTIFIER().getText(), Integer.parseUnsignedInt(descriptions.get(i).NUMBER().getText())));
        } else {
            bits = Collections.emptyMap();
        }
        td.typeDescription = bits;
    }

    @Override
    public void enterReferencedType(ReferencedTypeContext ctx) {
        TypeDescription td = checkedPeek(ctx, TypeDescription.class);
        td.typeDescription = ctx.getText();
    }

}
