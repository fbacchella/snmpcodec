package fr.jrds.snmpcodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static fr.jrds.snmpcodec.Asn1RfcExtractor.ASN1_DATE_FORMAT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Asn1RfcExtractorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testTwoDigitsYear() {
        ZonedDateTime lastUpdate = ZonedDateTime.parse("1701131715Z", ASN1_DATE_FORMAT);
        assertNotNull(lastUpdate);
        assertEquals(2017, lastUpdate.getYear());
        System.out.println("2 digits: " + lastUpdate);
    }

    @Test
    public void testFourDigitsYear() {
        ZonedDateTime lastUpdate = ZonedDateTime.parse("202601131715Z", ASN1_DATE_FORMAT);
        assertNotNull(lastUpdate);
        assertEquals(2026, lastUpdate.getYear());
        System.out.println("4 digits: " + lastUpdate);
    }

    @Test
    public void testSaveModuleReturn() throws Exception {
        String mibContent = "TEST-MIB DEFINITIONS ::= BEGIN\n" +
                "testModule MODULE-IDENTITY\n" +
                "    LAST-UPDATED \"202601131715Z\"\n" +
                "    ORGANIZATION \"jrds\"\n" +
                "    CONTACT-INFO \"contact\"\n" +
                "    DESCRIPTION \"test\"\n" +
                "    REVISION \"202601131715Z\"\n" +
                "    DESCRIPTION \"initial\"\n" +
                "    ::= { 1 3 6 1 4 1 1 }\n" +
                "END";

        Path tempFile = folder.newFile("test.mib").toPath();
        List<String> moduleLines = Arrays.asList(mibContent.split("\n"));
        ZonedDateTime rfcDate = ZonedDateTime.now();
        Asn1RfcExtractor extractor = new Asn1RfcExtractor();
        extractor.saveModule(tempFile, moduleLines, rfcDate);

        ZonedDateTime expected = ZonedDateTime.parse("202601131715Z", ASN1_DATE_FORMAT);
        assertEquals(FileTime.from(expected.toInstant()), Files.getLastModifiedTime(tempFile));
    }

    @Test
    public void testBadModulesMerged() {
        Asn1RfcExtractor extractor = new Asn1RfcExtractor();
        // From src/main/resources/badmodules.txt
        assertTrue("RFC1213-MIB should be in badModules", extractor.isBadModule("1212", "RFC1213-MIB"));
        assertTrue("HPR-MIB should be in badModules", extractor.isBadModule("1901", "COMMUNITY-BASED-SNMPv2"));
        // From src/test/resources/badmodules.txt
        assertTrue("EXTRA-BAD-MIB should be in badModules", extractor.isBadModule("EXTRA-BAD-MIB"));
        assertTrue("RFC-SPECIFIC-BAD-MIB should be in badModules", extractor.isBadModule("9999", "RFC-SPECIFIC-BAD-MIB"));
    }

    @Test
    public void testLoadBadModules() throws IOException {
        Path badModulesFile = folder.newFile("morebad.txt").toPath();
        Files.write(badModulesFile, Arrays.asList("NEW-BAD-MIB", "1234:SPECIFIC-BAD-MIB"));
        Asn1RfcExtractor extractor = new Asn1RfcExtractor();
        extractor.loadBadModules(badModulesFile);
        assertTrue("NEW-BAD-MIB should be in badModules", extractor.isBadModule("NEW-BAD-MIB"));
        assertTrue("SPECIFIC-BAD-MIB should be in badModules for RFC 1234", extractor.isBadModule("1234", "SPECIFIC-BAD-MIB"));
    }

    @Test
    public void testPageBreakWithEmptyLines() throws Exception {
        String[] rfcLines = {
                "TEST-MIB DEFINITIONS ::= BEGIN",
                "testModule MODULE-IDENTITY",
                "    LAST-UPDATED \"202601131715Z\"",
                "    ORGANIZATION \"jrds\"",
                "    CONTACT-INFO \"contact\"",
                "    DESCRIPTION \"test\"",
                "    REVISION \"202601131715Z\"",
                "    DESCRIPTION \"initial\"",
                "    ::= { 1 3 6 1 4 1 1 }",
                "                  [Page 1]",
                "\f",
                "",
                "RFC 1234             Some Title            January 2026",
                "",
                "next line in module",
                "END"
        };
        Path outDir = folder.newFolder("extract").toPath();
        Asn1RfcExtractor extractor = new Asn1RfcExtractor();
        extractor.extractAndSaveMibs(rfcLines, "1234", outDir);

        Path mibFile = outDir.resolve("rfc1234_mibs/TEST-MIB.mib");
        assertTrue("MIB file should be created", Files.exists(mibFile));
        List<String> lines = Files.readAllLines(mibFile);
        // It should contain "next line in module" and not the page footer/header or the extra empty lines
        assertTrue("Should contain next line", lines.contains("next line in module"));
        for (String line : lines) {
            assertNotNull(line);
            assertTrue("Should not contain page footer", !line.contains("[Page 1]"));
            assertTrue("Should not contain RFC header", !line.contains("RFC 1234"));
        }
    }

    @Test
    public void testBeginFalsePositive() throws Exception {
        String[] rfcLines = {
                "BEGIN-FALSE-POSITIVE-MIB DEFINITIONS ::= BEGIN",
                "    --  This is the BEGINNING of a test",
                "    internet      OBJECT IDENTIFIER ::= { iso org(3) dod(6) 1 }",
                "END",
                "ANOTHER-MIB DEFINITIONS ::= BEGIN",
                "    internet      OBJECT IDENTIFIER ::= { iso org(3) dod(6) 1 }",
                "    --  This is the ENDDING of a test",
                "END"
        };
        Path outDir = folder.newFolder("extract_false_positive").toPath();
        Asn1RfcExtractor extractor = new Asn1RfcExtractor();
        extractor.extractAndSaveMibs(rfcLines, "1235", outDir);

        Path mibFile1 = outDir.resolve("rfc1235_mibs/BEGIN-FALSE-POSITIVE-MIB.mib");
        Path mibFile2 = outDir.resolve("rfc1235_mibs/ANOTHER-MIB.mib");

        assertTrue("First MIB file should be created", Files.exists(mibFile1));
        assertTrue("Second MIB file should be created", Files.exists(mibFile2));
    }

}
