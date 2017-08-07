package fr.jrds.snmpcodec.parsing;

import java.util.HashMap;
import java.util.Map;

import fr.jrds.snmpcodec.parsing.ValueType.OidValue;
import fr.jrds.snmpcodec.smi.Oid.OidPath;

abstract class MibObject {

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

    static class TextualConventionObject extends MappedObject {
        OidValue oid;
        public TextualConventionObject() {
            super("TEXTUAL-CONVENTION");
        }
    }

    static class ObjectTypeObject extends StructuredObject<OidPath> {
        OidValue oid;
        public ObjectTypeObject() {
            super("OBJECT_TYPE");
        }
    }

    static class TrapTypeObject extends StructuredObject<Number> {
        OidValue oid;
        public TrapTypeObject() {
            super("TRAP_TYPE");
        }
    }

    static class ModuleIdentityObject extends StructuredObject<Number> {
        OidValue oid;
        public ModuleIdentityObject() {
            super("MODULE-IDENTITY");
        }
    }

    static class OtherMacroObject extends StructuredObject<OidPath> {
        OidValue oid;
        public OtherMacroObject(String name) {
            super(name);
        }
    }

    static class Revision {
        public final String description;
        public final String revision;
        public Revision(String description, String revision) {
            this.description = description;
            this.revision = revision;
        }
    }

}
