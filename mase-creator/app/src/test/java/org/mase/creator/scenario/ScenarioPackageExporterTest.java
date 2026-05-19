package org.mase.creator.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.MazeModel;
import org.mase.creator.model.PathStroke;
import org.mase.creator.trig.MazeTrigSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioPackageExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsServerReadyScenarioPackage() throws Exception {
        Path globalRules = createGlobalRules();
        MazeModel model = sampleModel();

        ScenarioPackageExportResult result = new ScenarioPackageExporter(new MazeTrigSerializer(), globalRules)
                .export(model, tempDir, "MaseCreator", false);

        Path root = result.packageRoot();
        assertTrue(Files.isRegularFile(root.resolve("scenario.properties")));
        assertTrue(Files.isRegularFile(root.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(root.resolve("README.md")));
        assertTrue(Files.isRegularFile(root.resolve(".env.example")));
        assertTrue(Files.isRegularFile(root.resolve("data/MaseCreator.trig")));
        assertTrue(Files.isRegularFile(root.resolve("rules/global/move.rq")));
        assertTrue(Files.isRegularFile(root.resolve("rules/global/ui.rq")));
        assertTrue(Files.isDirectory(root.resolve("rules/scenario")));
        try (Stream<Path> scenarioRules = Files.list(root.resolve("rules/scenario"))) {
            assertEquals(List.of(), scenarioRules.toList());
        }

        String properties = Files.readString(root.resolve("scenario.properties"), StandardCharsets.UTF_8);
        assertTrue(properties.contains("mase.scenario.id = masecreator"));
        assertTrue(properties.contains("mase.init.dataset = data/MaseCreator.trig"));
        assertTrue(properties.contains("mase.rules.execution.order = normalize_maze_locks*, normalize_maze_maps*, ui*, move_start*, move*"));

        String trig = Files.readString(root.resolve("data/MaseCreator.trig"), StandardCharsets.UTF_8);
        assertTrue(trig.contains("</maze>"));
        assertTrue(trig.contains("</cells/1/1>"));
        assertTrue(trig.contains("maze:exit </cells/999>"));

        String manifest = Files.readString(root.resolve("manifest.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"data\": [\"data/MaseCreator.trig\"]"));
        assertTrue(manifest.contains("\"rules\": [\"rules/**/*.rq\"]"));
        assertTrue(manifest.contains("\"README.md\""));
        assertTrue(manifest.contains("\".env.example\""));
        assertTrue(manifest.contains("rules/scenario is intentionally empty."));
        assertFalse(manifest.contains(tempDir.toAbsolutePath().normalize().toString()));
    }

    @Test
    void replacingExistingPackageRemovesStaleScenarioRules() throws Exception {
        Path globalRules = createGlobalRules();
        Path staleRule = tempDir.resolve("MaseCreator/rules/scenario/stale.rq");
        Files.createDirectories(staleRule.getParent());
        Files.writeString(staleRule, "CONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);

        new ScenarioPackageExporter(new MazeTrigSerializer(), globalRules)
                .export(sampleModel(), tempDir, "MaseCreator", true);

        assertFalse(Files.exists(staleRule));
        assertTrue(Files.isDirectory(tempDir.resolve("MaseCreator/rules/scenario")));
    }

    @Test
    void missingGlobalRulesDoesNotReplaceExistingPackage() throws Exception {
        Path marker = tempDir.resolve("MaseCreator/keep.txt");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "existing", StandardCharsets.UTF_8);

        ScenarioPackageExporter exporter = new ScenarioPackageExporter(
                new MazeTrigSerializer(),
                tempDir.resolve("missing-rules"));

        assertThrows(IOException.class, () -> exporter.export(sampleModel(), tempDir, "MaseCreator", true));
        assertTrue(Files.isRegularFile(marker));
    }

    private MazeModel sampleModel() {
        MazeModel model = MazeModel.blank(2, 2);
        PathStroke stroke = model.beginPath(new CellCoordinate(1, 1));
        model.continuePath(stroke, new CellCoordinate(1, 2));
        model.placeStart(new CellCoordinate(1, 1));
        model.placeExit(new CellCoordinate(1, 2));
        return model;
    }

    private Path createGlobalRules() throws Exception {
        Path rules = tempDir.resolve("source-rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("move.rq"), "DELETE { ?s ?p ?o } INSERT { ?s ?p ?o } WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        Files.writeString(rules.resolve("ui.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        return rules;
    }
}
