package fr.jrds.snmpcodec.smi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import fr.jrds.snmpcodec.MibException;

public class Trap {

    public final String name;
    public final List<String> variables;

    public Trap(Map<String, Object> details) throws MibException {
        if (! details.containsKey("SYMBOL")) {
            throw new MibException("Unfinished trap");
        }
        Symbol s = (Symbol) details.remove("SYMBOL");
        name = s.name;
        if (details.containsKey("VARIABLES")) {
            @SuppressWarnings("unchecked")
            List<String> variables = (List<String>) details.remove("VARIABLES");
            this.variables = Collections.unmodifiableList(variables);
        } else {
            this.variables = Collections.emptyList();
        }
    }

}
