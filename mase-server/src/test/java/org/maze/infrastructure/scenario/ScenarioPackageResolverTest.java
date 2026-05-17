package org.maze.infrastructure.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioPackageResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesScenarioPackageAndLoadsRulesRecursively() throws Exception {
        Path scenario = createBasePackage("demo");
        Files.createDirectories(scenario.resolve("rules/core"));
        Files.createDirectories(scenario.resolve("rules/custom/nested"));
        Files.createDirectories(scenario.resolve("rules-disabled"));
        Files.createDirectories(scenario.resolve("validation"));
        Files.createDirectories(scenario.resolve("agents"));
        Files.writeString(scenario.resolve("manifest.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules/root.rq"), "# root\nCONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules/core/active.rq"), "# active\nCONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules/custom/nested/also-active.rq"), "# nested\nCONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules-disabled/inactive.rq"), "# inactive\nCONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("validation/check.rq"), "ASK { ?s ?p ?o }\n", StandardCharsets.UTF_8);

        ScenarioPackage resolved = new ScenarioPackageResolver().resolve(scenario);

        assertEquals("demo", resolved.id());
        assertEquals(scenario.toAbsolutePath().normalize(), resolved.root());
        assertTrue(resolved.manifestFile().isPresent());
        assertTrue(resolved.agentsDirectory().isPresent());
        assertEquals(List.of("data/demo.trig"), relativePaths(scenario, resolved.dataFiles()));
        assertEquals(List.of(
                "rules/core/active.rq",
                "rules/custom/nested/also-active.rq",
                "rules/root.rq"), relativePaths(scenario, resolved.ruleFiles()));
        assertEquals(List.of("validation/check.rq"), relativePaths(scenario, resolved.validationFiles()));
        assertFalse(relativePaths(scenario, resolved.ruleFiles()).contains("rules-disabled/inactive.rq"));
    }

    @Test
    void rejectsMissingScenarioProperties() throws Exception {
        Path scenario = tempDir.resolve("missing-properties");
        Files.createDirectories(scenario);

        IOExceptionAssert.assertThrowsMessage(
                "scenario.properties",
                () -> new ScenarioPackageResolver().resolve(scenario));
    }

    @Test
    void rejectsMissingDataset() throws Exception {
        Path scenario = tempDir.resolve("missing-dataset");
        Files.createDirectories(scenario.resolve("rules"));
        Files.writeString(scenario.resolve("scenario.properties"),
                "mase.scenario.id = missing-dataset\n"
                        + "mase.init.dataset = data/missing.trig\n",
                StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules/root.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);

        IOExceptionAssert.assertThrowsMessage(
                "dataset pattern matched no files",
                () -> new ScenarioPackageResolver().resolve(scenario));
    }

    @Test
    void rejectsPackageWithoutActiveRules() throws Exception {
        Path scenario = createBasePackage("no-rules");

        IOExceptionAssert.assertThrowsMessage(
                "no active .rq files",
                () -> new ScenarioPackageResolver().resolve(scenario));
    }

    @Test
    void rejectsEscapingDatasetPath() throws Exception {
        Path scenario = tempDir.resolve("escape");
        Files.createDirectories(scenario.resolve("rules"));
        Files.writeString(scenario.resolve("scenario.properties"),
                "mase.scenario.id = escape\n"
                        + "mase.init.dataset = ../outside.trig\n",
                StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules/root.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);

        IOExceptionAssert.assertThrowsMessage(
                "escapes package root",
                () -> new ScenarioPackageResolver().resolve(scenario));
    }

    private Path createBasePackage(String id) throws Exception {
        Path scenario = tempDir.resolve(id);
        Files.createDirectories(scenario.resolve("data"));
        Files.createDirectories(scenario.resolve("rules"));
        Files.writeString(scenario.resolve("scenario.properties"),
                "mase.scenario.id = " + id + "\n"
                        + "mase.init.dataset = data/" + id + ".trig\n",
                StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("data/" + id + ".trig"), "@prefix ex: <http://example.org/> .\n", StandardCharsets.UTF_8);
        return scenario;
    }

    private List<String> relativePaths(Path root, List<Path> paths) {
        return paths.stream()
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .toList();
    }

    private static final class IOExceptionAssert {
        private static void assertThrowsMessage(String expectedText, ThrowingRunnable runnable) {
            Exception exception = assertThrows(Exception.class, runnable::run);
            assertTrue(exception.getMessage().contains(expectedText),
                    "Expected message to contain '" + expectedText + "' but was: " + exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
