package fr.jrds.snmpcodec.smi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Trap {

    public final String name;
    public final List<String> variables;

    public Trap(Map<String, Object> details) {
        Symbol s = (Symbol) details.get("SYMBOL");
        name = s.name;
        @SuppressWarnings("unchecked")
        List<String> variables = (List<String>) details.get("VARIABLES");
        this.variables = Collections.unmodifiableList(variables);
    }

}
