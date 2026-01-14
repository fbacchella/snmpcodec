package fr.jrds.snmpcodec;

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

}
