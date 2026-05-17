package org.maze.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsScenarioPackageConfigurationWithResolvedDatasetPath() throws Exception {
        Path scenario = tempDir.resolve("packaged");
        Files.createDirectories(scenario.resolve("data"));
        Files.createDirectories(scenario.resolve("rules"));
        Files.writeString(scenario.resolve("data/packaged.trig"), "@prefix ex: <http://example.org/> .\n", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("rules/root.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n", StandardCharsets.UTF_8);
        Files.writeString(scenario.resolve("scenario.properties"),
                "mase.scenario.id = packaged\n"
                        + "mase.init.dataset = data/packaged.trig\n"
                        + "mase.rules.path = rules/**/*.rq\n"
                        + "mase.rules.execution.order = unlock*, move*\n"
                        + "mase.transaction.trace = off\n",
                StandardCharsets.UTF_8);

        ServerConfiguration config = ServerConfiguration.forScenarioPackage(scenario);

        assertTrue(config.isScenarioPackageMode());
        assertEquals("packaged", config.getTaskName());
        assertEquals(scenario.resolve("data/packaged.trig").toAbsolutePath().normalize().toString(), config.getInitDataset());
        assertEquals(List.of("unlock*", "move*"), config.getRuleExecutionOrder());
        assertTrue(config.getScenarioPackage().isPresent());
        assertFalse(config.getRawProperties().containsKey("mase.server.protocol"));
    }
}
