package fr.jrds.snmpcodec;

import java.io.IOException;

import javax.print.PrintException;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Test;
import org.antlr.v4.gui.Trees;

import fr.jrds.snmpcodec.parsing.ASNLexer;
import fr.jrds.snmpcodec.parsing.ASNParser;

public class x509 {

    @Test
    public void read() throws PrintException, IOException {
        String certificate = "Certificate  ::=  SEQUENCE  {\n" + "        tbsCertificate       TBSCertificate,\n" + "        signatureAlgorithm   AlgorithmIdentifier,\n" + "        signatureValue       BIT STRING  }";
        var cs = CharStreams.fromStream(x509.class.getResourceAsStream("/x509.txt"));
        ASNLexer lexer = new ASNLexer(cs);
        var tokens = new CommonTokenStream(lexer);
        var parser = new ASNParser(tokens);
        var tree = parser.assignmentList();
        Trees.save(tree, parser, "/tmp/fragment.ps");
    }
}
