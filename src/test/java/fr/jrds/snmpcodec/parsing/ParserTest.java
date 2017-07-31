package fr.jrds.snmpcodec.parsing;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.smi.DeclaredType;
import fr.jrds.snmpcodec.smi.Symbol;

public class ParserTest {

    @Test
    public void checkCodecs() throws URISyntaxException {
        MibStore resolver = new MibStore();
        MibLoader loader = new MibLoader(resolver);
        loader.load(Paths.get(getClass().getClassLoader().getResource("custommib.txt").toURI()));
        
        Symbol tableSymbol = new Symbol("CUSTOM","table");

        DeclaredType<?> table = resolver.types.get(tableSymbol);
        
        Assert.assertFalse(table.isCodec());
        Assert.assertFalse(resolver.codecs.containsKey(tableSymbol));

    }

}
