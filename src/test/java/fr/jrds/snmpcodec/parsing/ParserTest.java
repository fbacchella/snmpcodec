package fr.jrds.snmpcodec.parsing;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.smi.Symbol;
import fr.jrds.snmpcodec.smi.Syntax;

public class ParserTest {

    @Test
    public void checkCodecs() throws URISyntaxException {
        MibStore resolver = new MibStore();
        MibLoader loader = new MibLoader(resolver);
        loader.load(Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()));
        
        Symbol tableSymbol = new Symbol("CUSTOM","table");

        Syntax table = resolver.codecs.get(tableSymbol);
        
        Assert.assertFalse(resolver.codecs.containsKey(tableSymbol));

    }

}
