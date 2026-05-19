package org.maze.infrastructure.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.junit.jupiter.api.Test;
import org.maze.application.MazeLayoutService;
import org.maze.application.MazeRuleService;
import org.maze.application.tx.TransactionTraceMode;
import org.maze.domain.rules.MazeRule;
import org.maze.infrastructure.config.ServerConfiguration;
import org.maze.infrastructure.rdf.DataLoader;
import org.maze.infrastructure.rdf.RepositoryFactory;

class ScenarioPackageStartupTest {

    private static final URI RDF_BASE_URI = URI.create("http://127.0.1.1:8080/gsp/");

    @Test
    void packagedSmallMazeLoadsDataAndRunsStartupRules() throws Exception {
        ServerConfiguration config = ServerConfiguration.forScenarioPackage(Path.of("scenarios/smallmaze"));
        ScenarioPackage scenarioPackage = config.getScenarioPackage().orElseThrow();
        SailRepository repository = new RepositoryFactory().createRepository(config);

        new DataLoader().loadData(repository, config.getInitDataset(), RDF_BASE_URI);
        MazeRuleService ruleService = new MazeRuleService(
                repository,
                scenarioPackage.ruleFiles(),
                scenarioPackage.root(),
                config.getRuleExecutionOrder());
        List<String> ruleNames = ruleService.getRules().stream()
                .map(MazeRule::getName)
                .toList();
        assertTrue(ruleNames.contains("rules/scenario/ui-keys"));

        try (SailRepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            ruleService.executeRules(conn);
            conn.commit();
        }

        MazeLayoutService layoutService = new MazeLayoutService(repository);
        assertEquals("SmallMaze", layoutService.getMazeScenarioName());
        assertEquals(5, layoutService.getMazeLayout().width);
        assertEquals(5, layoutService.getMazeLayout().height);
        assertTrue(layoutService.getUiSnapshot().size() > 0);
        assertTrue(layoutService.getUiSnapshot().stream()
                .anyMatch(ui -> ui.id.endsWith("#ui-greenkey")));
        assertTrue(layoutService.getUiSnapshot().stream()
                .anyMatch(ui -> ui.id.endsWith("#ui-redkey")));
    }

    @Test
    void packagedCcrsUsesPackageLocalRulesInExpectedOrder() throws Exception {
        ServerConfiguration config = ServerConfiguration.forScenarioPackage(Path.of("scenarios/ccrs"));
        ScenarioPackage scenarioPackage = config.getScenarioPackage().orElseThrow();
        MazeRuleService ruleService = new MazeRuleService(
                new RepositoryFactory().createRepository(config),
                scenarioPackage.ruleFiles(),
                scenarioPackage.root(),
                config.getRuleExecutionOrder());

        List<String> ruleNames = ruleService.getRules().stream()
                .map(MazeRule::getName)
                .toList();

        int normalizeIndex = ruleNames.indexOf("rules/global/normalize_maze_locks");
        int ccrsIndex = ruleNames.indexOf("rules/scenario/ccrs");
        int unlockIndex = ruleNames.indexOf("rules/scenario/unlock-keys");
        int cleanupIndex = ruleNames.indexOf("rules/scenario/cleanup-move-success-events");
        int moveIndex = ruleNames.indexOf("rules/global/move");

        assertTrue(normalizeIndex >= 0);
        assertTrue(ccrsIndex > normalizeIndex);
        assertTrue(unlockIndex > ccrsIndex);
        assertTrue(cleanupIndex > unlockIndex);
        assertTrue(moveIndex > cleanupIndex);
        assertTrue(ruleNames.stream().noneMatch(name -> name.contains("rules-disabled")));
    }

    @Test
    void packagedConfigurationDoesNotCarryDeprecatedProtocolFlag() throws Exception {
        ServerConfiguration config = ServerConfiguration.forScenarioPackage(Path.of("scenarios/smallmaze"));

        assertTrue(config.isScenarioPackageMode());
        assertEquals(TransactionTraceMode.FULL, config.getTransactionTraceMode());
        assertTrue(config.getRawProperties().stringPropertyNames().stream()
                .noneMatch("mase.server.protocol"::equals));
    }
}
