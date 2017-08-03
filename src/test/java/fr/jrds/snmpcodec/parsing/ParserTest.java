package fr.jrds.snmpcodec.parsing;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import fr.jrds.snmpcodec.Mib;
import fr.jrds.snmpcodec.smi.Symbol;

public class ParserTest {

    @Test
    public void checkCodecs() throws URISyntaxException {
        Mib resolver = new Mib();
        MibLoader loader = new MibLoader(resolver);
        loader.load(Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()));
        
        Symbol tableSymbol = new Symbol("CUSTOM","table");

        SyntaxDeclaration<?> table = resolver.types.get(tableSymbol);
        
        Assert.assertFalse(table.isCodec());
        Assert.assertFalse(resolver.codecs.containsKey(tableSymbol));

    }

}
