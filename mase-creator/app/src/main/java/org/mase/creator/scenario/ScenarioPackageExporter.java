package org.mase.creator.scenario;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.MazeModel;
import org.mase.creator.trig.MazeTrigSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class ScenarioPackageExporter {

    public static final String DEFAULT_PACKAGE_NAME = "MaseCreator";

    private static final String RULE_ORDER =
            "normalize_maze_locks*, normalize_maze_maps*, ui*, move_start*, move*";
    private static final String GLOBAL_RULE_SOURCE = "mase-server/scenarios/masecreator/rules/global";
    private static final List<String> METADATA_FILES = List.of(
            "scenario.properties",
            "manifest.json",
            "README.md",
            ".env.example",
            "rules-disabled/README.md",
            "validation/README.md",
            "agents/README.md");

    private final MazeTrigSerializer serializer;
    private final Path globalRulesSource;

    public ScenarioPackageExporter(MazeTrigSerializer serializer) {
        this(serializer, discoverDefaultGlobalRulesSource());
    }

    public ScenarioPackageExporter(MazeTrigSerializer serializer, Path globalRulesSource) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.globalRulesSource = Objects.requireNonNull(globalRulesSource, "globalRulesSource")
                .toAbsolutePath()
                .normalize();
    }

    public ScenarioPackageExportResult export(
            MazeModel model,
            Path outputDirectory,
            String packageName,
            boolean replaceExisting
    ) throws IOException {
        Objects.requireNonNull(model, "model");
        Path normalizedOutputDirectory = outputDirectory.toAbsolutePath().normalize();
        String normalizedPackageName = ScenarioPackageNames.normalizePackageDirectoryName(packageName);
        String scenarioId = ScenarioPackageNames.scenarioIdFromPackageName(normalizedPackageName);
        String dataFileName = ScenarioPackageNames.trigFileNameFromPackageName(normalizedPackageName);
        Path packageRoot = normalizedOutputDirectory.resolve(normalizedPackageName).normalize();
        ensureInsideOutputDirectory(normalizedOutputDirectory, packageRoot);
        List<Path> globalRuleFiles = discoverGlobalRuleFiles();

        preparePackageRoot(normalizedOutputDirectory, packageRoot, replaceExisting);

        List<String> generatedFiles = new ArrayList<>();
        Path dataDirectory = packageRoot.resolve("data");
        Path globalRulesDirectory = packageRoot.resolve("rules").resolve("global");
        Path scenarioRulesDirectory = packageRoot.resolve("rules").resolve("scenario");
        Path rulesDisabledDirectory = packageRoot.resolve("rules-disabled");
        Path validationDirectory = packageRoot.resolve("validation");
        Path agentsDirectory = packageRoot.resolve("agents");

        Files.createDirectories(dataDirectory);
        Files.createDirectories(globalRulesDirectory);
        Files.createDirectories(scenarioRulesDirectory);
        Files.createDirectories(rulesDisabledDirectory);
        Files.createDirectories(validationDirectory);
        Files.createDirectories(agentsDirectory);

        Path dataFile = dataDirectory.resolve(dataFileName);
        Files.writeString(dataFile, serializer.serialize(model), StandardCharsets.UTF_8);
        generatedFiles.add(relativePath(packageRoot, dataFile));

        generatedFiles.addAll(copyGlobalRules(packageRoot, globalRulesDirectory, globalRuleFiles));
        List<String> manifestGeneratedFiles = new ArrayList<>(generatedFiles);
        manifestGeneratedFiles.addAll(METADATA_FILES);

        writeGeneratedFile(packageRoot, "scenario.properties",
                scenarioProperties(scenarioId, normalizedPackageName, dataFileName), generatedFiles);
        writeGeneratedFile(packageRoot, "manifest.json",
                manifestJson(model, scenarioId, normalizedPackageName, dataFileName, manifestGeneratedFiles), generatedFiles);
        writeGeneratedFile(packageRoot, "README.md",
                readme(normalizedPackageName, dataFileName), generatedFiles);
        writeGeneratedFile(packageRoot, ".env.example",
                envExample(), generatedFiles);
        writeGeneratedFile(packageRoot, "rules-disabled/README.md",
                rulesDisabledReadme(), generatedFiles);
        writeGeneratedFile(packageRoot, "validation/README.md",
                validationReadme(), generatedFiles);
        writeGeneratedFile(packageRoot, "agents/README.md",
                agentsReadme(), generatedFiles);

        return new ScenarioPackageExportResult(packageRoot, generatedFiles);
    }

    private void preparePackageRoot(Path outputDirectory, Path packageRoot, boolean replaceExisting) throws IOException {
        Files.createDirectories(outputDirectory);
        if (!Files.exists(packageRoot)) {
            Files.createDirectories(packageRoot);
            return;
        }
        if (!replaceExisting) {
            throw new FileAlreadyExistsException(packageRoot.toString());
        }
        deleteRecursively(packageRoot);
        Files.createDirectories(packageRoot);
    }

    private void ensureInsideOutputDirectory(Path outputDirectory, Path packageRoot) throws IOException {
        if (!packageRoot.startsWith(outputDirectory) || packageRoot.equals(outputDirectory)) {
            throw new IOException("Scenario package path escapes the output directory: " + packageRoot);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private List<Path> discoverGlobalRuleFiles() throws IOException {
        if (!Files.isDirectory(globalRulesSource)) {
            throw new IOException("Global rule source directory not found: " + globalRulesSource);
        }

        try (Stream<Path> stream = Files.walk(globalRulesSource)) {
            List<Path> ruleFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".rq"))
                    .sorted(Comparator.comparing(path -> globalRulesSource.relativize(path).toString().replace('\\', '/')))
                    .toList();
            if (ruleFiles.isEmpty()) {
                throw new IOException("Global rule source directory contains no .rq files: " + globalRulesSource);
            }
            return ruleFiles;
        }
    }

    private List<String> copyGlobalRules(
            Path packageRoot,
            Path globalRulesDirectory,
            List<Path> ruleFiles
    ) throws IOException {
        List<String> copied = new ArrayList<>();
        for (Path source : ruleFiles) {
            Path relative = globalRulesSource.relativize(source);
            Path target = globalRulesDirectory.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            copied.add(relativePath(packageRoot, target));
        }
        return copied;
    }

    private void writeGeneratedFile(
            Path packageRoot,
            String relativePath,
            String content,
            List<String> generatedFiles
    ) throws IOException {
        Path target = packageRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        generatedFiles.add(relativePath(packageRoot, target));
    }

    private String scenarioProperties(String scenarioId, String packageName, String dataFileName) {
        return "# " + packageName + " scenario package" + System.lineSeparator()
                + "mase.scenario.id = " + scenarioId + System.lineSeparator()
                + "mase.scenario.name = " + packageName + System.lineSeparator()
                + "mase.init.dataset = data/" + dataFileName + System.lineSeparator()
                + "# Transaction trace mode: off, summary headers/rule count, or full per-triple debug diffs" + System.lineSeparator()
                + "mase.transaction.trace = summary" + System.lineSeparator()
                + "mase.rules.execution.order = " + RULE_ORDER + System.lineSeparator();
    }

    private String manifestJson(
            MazeModel model,
            String scenarioId,
            String packageName,
            String dataFileName,
            List<String> generatedFiles
    ) {
        return "{" + System.lineSeparator()
                + "  \"contractVersion\": \"0.1\"," + System.lineSeparator()
                + "  \"id\": " + jsonString(scenarioId) + "," + System.lineSeparator()
                + "  \"name\": " + jsonString(packageName) + "," + System.lineSeparator()
                + "  \"generator\": \"mase-creator\"," + System.lineSeparator()
                + "  \"generatedOn\": " + jsonString(LocalDate.now().toString()) + "," + System.lineSeparator()
                + "  \"entrypoint\": \"scenario.properties\"," + System.lineSeparator()
                + "  \"start\": " + jsonString(startDescription(model)) + "," + System.lineSeparator()
                + "  \"exit\": " + jsonString(exitDescription(model)) + "," + System.lineSeparator()
                + "  \"files\": {" + System.lineSeparator()
                + "    \"data\": [\"data/" + jsonEscape(dataFileName) + "\"]," + System.lineSeparator()
                + "    \"rules\": [\"rules/**/*.rq\"]" + System.lineSeparator()
                + "  }," + System.lineSeparator()
                + "  \"generatedFiles\": [" + System.lineSeparator()
                + renderJsonArray(generatedFiles)
                + "  ]," + System.lineSeparator()
                + "  \"warnings\": [" + System.lineSeparator()
                + "    \"Scenario-specific rules are not generated yet; rules/scenario is intentionally empty.\"" + System.lineSeparator()
                + "  ]" + System.lineSeparator()
                + "}" + System.lineSeparator();
    }

    private String readme(String packageName, String dataFileName) {
        return "# " + packageName + " Scenario" + System.lineSeparator()
                + System.lineSeparator()
                + "This scenario package was generated by MASE Creator." + System.lineSeparator()
                + System.lineSeparator()
                + "Run it from `mase-server` with:" + System.lineSeparator()
                + System.lineSeparator()
                + "```powershell" + System.lineSeparator()
                + ".\\gradlew.bat runMase --args='--scenario \"path\\to\\" + packageName + "\"'" + System.lineSeparator()
                + "```" + System.lineSeparator()
                + System.lineSeparator()
                + "The maze data is in [" + dataFileName + "](<data/" + dataFileName + ">). Active global rules are under [rules/global](rules/global). "
                + "The [rules/scenario](rules/scenario) folder is currently empty and reserved for future scenario-specific rules."
                + System.lineSeparator();
    }

    private String envExample() {
        return "# Optional local settings for future creator-generated scenarios." + System.lineSeparator()
                + "# Do not put real secrets in exported packages." + System.lineSeparator();
    }

    private String rulesDisabledReadme() {
        return "# Disabled Rules" + System.lineSeparator()
                + System.lineSeparator()
                + "Files in this directory are not loaded by `mase-server`. Move `.rq` files here when they should stay with the package but not run."
                + System.lineSeparator();
    }

    private String validationReadme() {
        return "# Validation" + System.lineSeparator()
                + System.lineSeparator()
                + "Optional scenario-specific validation queries can be added here as `.rq` files."
                + System.lineSeparator();
    }

    private String agentsReadme() {
        return "# Agents" + System.lineSeparator()
                + System.lineSeparator()
                + "Optional scenario-owned agent source or launch notes can be added here."
                + System.lineSeparator();
    }

    private String startDescription(MazeModel model) {
        return model.startCell()
                .map(CellCoordinate::path)
                .or(() -> model.rawStartIri().map(String::strip))
                .orElse("default generated maze start");
    }

    private String exitDescription(MazeModel model) {
        return model.exitSourceCell()
                .map(CellCoordinate::path)
                .map(path -> path + " -> /cells/999")
                .orElse("no exit cell marked");
    }

    private String relativePath(Path root, Path file) {
        return root.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private String renderJsonArray(List<String> values) {
        StringBuilder array = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            array.append("    ").append(jsonString(values.get(i)));
            if (i < values.size() - 1) {
                array.append(",");
            }
            array.append(System.lineSeparator());
        }
        return array.toString();
    }

    private String jsonString(String value) {
        return "\"" + jsonEscape(value) + "\"";
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static Path discoverDefaultGlobalRulesSource() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidateRoot = current; candidateRoot != null; candidateRoot = candidateRoot.getParent()) {
            Path candidate = candidateRoot.resolve(GLOBAL_RULE_SOURCE).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return current.resolve(GLOBAL_RULE_SOURCE).toAbsolutePath().normalize();
    }
}
