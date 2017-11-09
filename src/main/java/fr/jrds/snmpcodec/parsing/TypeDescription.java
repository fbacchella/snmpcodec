package fr.jrds.snmpcodec.parsing;

import java.util.Map;

import fr.jrds.snmpcodec.smi.Bits;
import fr.jrds.snmpcodec.smi.Constraint;
import fr.jrds.snmpcodec.smi.AnnotedSyntax;
import fr.jrds.snmpcodec.smi.Referenced;
import fr.jrds.snmpcodec.smi.SmiType;
import fr.jrds.snmpcodec.smi.Syntax;
import fr.jrds.snmpcodec.smi.Table;
import fr.jrds.snmpcodec.smi.TableEntry;

public class TypeDescription {
    Asn1Type type;
    Object typeDescription = null;
    Map<Number, String> names;
    Constraint constraints = null;

    public Syntax getSyntax(ModuleListener listener) {
        Syntax trySyntax;
        switch (type) {
        case referencedType:
            trySyntax = new Referenced(listener.resolveSymbol((String) typeDescription));
            break;
        case octetStringType:
            trySyntax = SmiType.OctetString;
            break;
        case integerType:
            trySyntax = SmiType.INTEGER;
            break;
        case objectidentifiertype:
            trySyntax = SmiType.ObjID;
            break;
        case nullType:
            trySyntax = SmiType.Null;
            break;
        case bitsType:
            @SuppressWarnings("unchecked")
            Map<String, Integer> bitsEnumeration = (Map<String, Integer>) typeDescription;
            return new Bits(bitsEnumeration, constraints);
        case sequenceType:
            @SuppressWarnings("unchecked")
            Map<String, Syntax> columns = (Map<String, Syntax>) typeDescription;
            return new TableEntry(columns);
        case sequenceOfType:
            TypeDescription td = (TypeDescription) typeDescription;
            return new Table(listener.resolveSymbol((String) td.typeDescription));
        case bitStringType:
        case choiceType:
        case enumeratedType:
        case objectClassFieldType:
        case setType:
        case setOfType:
        default:
            return new NullSyntax();
        }
        if (names != null || constraints != null) {
            return new AnnotedSyntax(trySyntax, names, constraints);
        } else {
            return trySyntax;
        }
    }

    @Override
    public String toString() {
        return "" + type + (typeDescription != null ? " " + typeDescription : "");
    }


}
