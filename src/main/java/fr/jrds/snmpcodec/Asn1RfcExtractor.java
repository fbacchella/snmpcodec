package fr.jrds.snmpcodec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import fr.jrds.snmpcodec.parsing.ASNLexer;
import fr.jrds.snmpcodec.parsing.ASNParser;
import fr.jrds.snmpcodec.parsing.ASNParser.AssignmentContext;
import fr.jrds.snmpcodec.parsing.ASNParser.FileContentContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleDefinitionContext;
import fr.jrds.snmpcodec.parsing.ASNParser.ModuleIdentityAssignementContext;

public class Asn1RfcExtractor {

    private static final String RFC_URL_TEMPLATE = "https://www.rfc-editor.org/rfc/rfc%s.txt";

    private static final Pattern FOOTER = Pattern.compile(".*\\s+\\[Page\\s+\\d+\\]");
    private static final Pattern HEADER = Pattern.compile("^RFC\\s+\\d+\\s+.+?\\s+(\\w+\\s+\\d{4})$");
    private static final Pattern DEFINITION_LINE = Pattern.compile("DEFINITIONS(\\s+[A-Z]+)*\\s+::=");
    private static final Pattern MODULE_NAME_LINE = Pattern.compile("(\\s*)([a-zA-Z0-9-]+)\\s*(\\{.*\\})?\\s*DEFINITIONS(\\s+[A-Z]+)*\\s+::=.*");

    private final List<String> badModules = new ArrayList<>();

    public Asn1RfcExtractor() {
        try {
            Enumeration<URL> urls = Asn1RfcExtractor.class.getClassLoader().getResources("badmodules.txt");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    badModules.addAll(reader.lines()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .collect(Collectors.toList()));
                } catch (IOException e) {
                    System.err.println("Failed to load bad modules list from " + url + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to search for bad modules files: " + e.getMessage());
        }
    }

    public void loadBadModules(Path badModulesFile) {
        try (BufferedReader reader = Files.newBufferedReader(badModulesFile, StandardCharsets.UTF_8)) {
            badModules.addAll(reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            System.err.println("Failed to load bad modules list from " + badModulesFile + ": " + e.getMessage());
        }
    }

    static final DateTimeFormatter ASN1_DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendValueReduced(ChronoField.YEAR, 2, 4, 1970)
            .appendPattern("MMddHHmm")
            .appendOffset("+HHmm", "Z")
            .toFormatter();
    static final DateTimeFormatter HEADER_DATE_FORMAT = new DateTimeFormatterBuilder()
                                          .appendPattern("MMMM yyyy")
                                          .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                          .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                                          .toFormatter(Locale.ENGLISH);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Asn1RfcExtractor [-d extract_dir] [-b badmodules_file] <rfc_number>+");
            System.exit(1);
        }
        List<String> rfcs = new ArrayList<>(args.length);
        Path extractPath = Paths.get(".");
        List<Path> badModulesFiles = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-d")) {
                i++;
                extractPath = Paths.get(args[i]);
            } else if (args[i].equals("-b")) {
                i++;
                badModulesFiles.add(Paths.get(args[i]));
            } else {
                rfcs.add(args[i]);
            }
        }
        try {
            Asn1RfcExtractor extractor = new Asn1RfcExtractor();
            for (Path badModulesFile : badModulesFiles) {
                extractor.loadBadModules(badModulesFile);
            }
            for (String rfc : rfcs) {
                String[] rfcLines = extractor.downloadRfc(rfc);
                extractor.extractAndSaveMibs(rfcLines, rfc, extractPath);
            }
        } catch (IOException e) {
            System.err.println("Error processing RFC: " + e.getMessage());
            System.exit(1);
        }
    }

    private String[] downloadRfc(String rfcNumber) throws IOException {
        URL url = new URL(String.format(RFC_URL_TEMPLATE, rfcNumber));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toArray(String[]::new);
        }
    }

    void extractAndSaveMibs(String[] rfcLines, String rfcNumber, Path rootDir) throws IOException {
        Path extractDir = Path.of("rfc" + rfcNumber + "_mibs");
        Path outDir = rootDir.resolve(extractDir);
        Files.createDirectories(outDir);
        Matcher headerMatcher = HEADER.matcher("");
        // Pre-clean RFC lines to remove footers and surrounding empty lines
        Deque<String> moduleLines = new ArrayDeque<>();
        boolean inModule = false;
        String moduleName = null;
        int count = 0;
        int indent = 0;
        int beginEndPair = 0;
        ZonedDateTime rfcDate = null;
        for (int i = 0; i < rfcLines.length; i++) {
            String line = rfcLines[i];
            if (DEFINITION_LINE.matcher(line).find()) {
                inModule = true;
                StringBuilder buffer = new StringBuilder();
                Matcher m = MODULE_NAME_LINE.matcher("");
                int firstLine = -1;
                for (int j = i; j >= 0; j--) {
                    buffer.insert(0, " ");
                    buffer.insert(0, rfcLines[j]);
                    m.reset(buffer);
                    if (m.matches()) {
                        firstLine = j;
                        break;
                    }
                }
                indent = m.group(1).length();
                moduleName = m.group(2);
                for (int j = firstLine; j <= i ; j++) {
                    lineWithoutPrefix(rfcLines[j], moduleLines, indent);
                }
            } else if (inModule && line.strip().startsWith("END") && beginEndPair == 0) {
                if (isBadModule(rfcNumber, moduleName)) {
                    System.out.println("Skipping bad module: " + moduleName + " from RFC " + rfcNumber);
                } else {
                    System.out.println("Found module: " + moduleName);
                    lineWithoutPrefix(line, moduleLines, indent);
                    Path modulePath = outDir.resolve(moduleName + ".mib");
                    saveModule(modulePath, moduleLines, rfcDate);
                    count++;
                }
                moduleLines.clear();
                inModule = false;
            }  else if (inModule && line.contains("\f")) {
                int j = i;
                while (j < rfcLines.length && (rfcLines[j].contains("\f") || rfcLines[j].strip().isEmpty())) {
                    j++;
                }
                if (j < rfcLines.length && FOOTER.matcher(rfcLines[i - 1]).matches() && headerMatcher.reset(rfcLines[j]).matches()) {
                    if (rfcDate == null) {
                        rfcDate = ZonedDateTime.parse(headerMatcher.group(1), HEADER_DATE_FORMAT.withZone(java.time.ZoneOffset.UTC));
                    }
                    // Remove the footer line
                    moduleLines.removeLast();
                    // Detect footer/header
                    for (int k = 0; k < 3 && !moduleLines.isEmpty(); k++) {
                        if (! moduleLines.getLast().isBlank()) {
                            break;
                        } else {
                            moduleLines.removeLast();
                        }
                    }
                    i = j;
                } else {
                    lineWithoutPrefix(line, moduleLines, indent);
                }
            } else if (inModule && line.contains("BEGIN")) {
                lineWithoutPrefix(line, moduleLines, indent);
                beginEndPair++;
            } else if (inModule && line.contains("END")) {
                lineWithoutPrefix(line, moduleLines, indent);
                beginEndPair--;
            } else if (inModule) {
                lineWithoutPrefix(line, moduleLines, indent);
            }
        }
        if (count == 0) {
            System.out.println("No MIB modules found in RFC " + rfcNumber);
        } else {
            System.out.println("Total modules extracted: " + count);
        }
    }

    private void lineWithoutPrefix(String line, Deque<String> moduleLines, int indent) {
        String indentPrefix = line.substring(0, Math.min(indent, line.length()));
        // indentPrefix should contain only spaces
        if (indentPrefix.isBlank()) {
            moduleLines.add(line.substring(Math.min(indent, line.length())));
        } else {
            moduleLines.add(line);
        }
    }

    void saveModule(Path extractPath, Iterable<String> moduleContent, ZonedDateTime rfcDate) throws IOException {
        Files.write(extractPath, moduleContent, StandardCharsets.UTF_8);
        if (rfcDate != null) {
            Files.setLastModifiedTime(extractPath, FileTime.from(rfcDate.toInstant()));
        }
        String content = StreamSupport.stream(moduleContent.spliterator(), false).collect(Collectors.joining("\n"));
        CharStream cs = CharStreams.fromString(content);
        Lexer lexer = new ASNLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        ASNParser parser = new ASNParser(tokens);
        parser.removeErrorListeners();
        AtomicBoolean errorFound = new AtomicBoolean(false);
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                if (! errorFound.get()) {
                    System.err.println(extractPath + ":line " + line + ":" + charPositionInLine + " " + msg);
                    errorFound.set(true);
                }
            }
        });
        parser.setBuildParseTree(true);
        FileContentContext ctx = parser.fileContent();
        ModuleDefinitionContext mdctx = ctx.moduleDefinition(0);
        for (AssignmentContext assctx: mdctx.moduleBody().assignmentList().assignment()) {
            ModuleIdentityAssignementContext modidctx = assctx.assignementType().moduleIdentityAssignement();
            if (modidctx != null && modidctx.lu != null) {
                try {
                    String dateStr = modidctx.lu.getText().replace("\"", "");
                    ZonedDateTime lastUpdate = ZonedDateTime.parse(dateStr, ASN1_DATE_FORMAT);
                    Files.setLastModifiedTime(extractPath, FileTime.from(lastUpdate.toInstant()));
                    return;
                } catch (Exception e) {
                    System.err.println("Failed to parse date: " + modidctx.lu.getText());
                }
            }
        }
        if (rfcDate != null) {
            Files.setLastModifiedTime(extractPath, FileTime.from(rfcDate.toInstant()));
        } else {
            System.err.println("Failed to resolve date in " + extractPath);
        }
    }

    public boolean isBadModule(String moduleName) {
        return badModules.contains(moduleName);
    }

    public boolean isBadModule(String rfcNumber, String moduleName) {
        return badModules.contains(rfcNumber + ":" + moduleName) || badModules.contains(moduleName);
    }

}
