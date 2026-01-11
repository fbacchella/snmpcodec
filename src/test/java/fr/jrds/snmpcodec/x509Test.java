package fr.jrds.snmpcodec;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import fr.jrds.snmpcodec.parsing.MibLoader;

public class x509Test {

    @Test
    public void read() {
        Path x509Explicit = Paths.get(getClass().getClassLoader().getResource("x509Explicit.txt").getFile());
        Path x509Implicit = Paths.get(getClass().getClassLoader().getResource("x509Implicit.txt").getFile());
        MibLoader loader = new MibLoader();
        loader.load(x509Explicit);
        loader.load(x509Implicit);
        MibStore store = loader.buildTree();
        Assert.assertEquals(18, store.syntaxes.size());
        Assert.assertEquals(51, store.names.size());
        Assert.assertEquals(2, store.modules.size());
        Assert.assertTrue(store.modules.contains("PKIX1Explicit88"));
        Assert.assertTrue(store.modules.contains("PKIX1Implicit88"));
    }

}
