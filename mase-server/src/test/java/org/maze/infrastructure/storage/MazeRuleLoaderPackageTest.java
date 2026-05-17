package org.maze.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.maze.domain.rules.MazeRule;

class MazeRuleLoaderPackageTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPackageRulesWithNamesRelativeToScenarioRootAndOrdersBySimpleName() throws Exception {
        Path scenario = tempDir.resolve("scenario");
        Files.createDirectories(scenario.resolve("rules/core"));
        Files.createDirectories(scenario.resolve("rules/custom/nested"));
        Path rootRule = writeRule(scenario.resolve("rules/root.rq"), "# root rule\nCONSTRUCT WHERE { ?s ?p ?o }\n");
        Path activeRule = writeRule(scenario.resolve("rules/core/active.rq"), "# active rule\nCONSTRUCT WHERE { ?s ?p ?o }\n");
        Path nestedRule = writeRule(scenario.resolve("rules/custom/nested/also-active.rq"), "# nested rule\nDELETE { ?s ?p ?o } INSERT { ?s ?p ?o } WHERE { ?s ?p ?o }\n");

        MazeRuleLoader loader = new MazeRuleLoader();

        List<MazeRule> rules = loader.loadRulesFromPaths(
                List.of(rootRule, activeRule, nestedRule),
                scenario,
                List.of("active*", "also*", "root*"));

        assertEquals(List.of(
                "rules/core/active",
                "rules/custom/nested/also-active",
                "rules/root"),
                rules.stream().map(MazeRule::getName).toList());
        assertEquals(List.of(
                MazeRule.RuleType.CONSTRUCT,
                MazeRule.RuleType.UPDATE,
                MazeRule.RuleType.CONSTRUCT),
                rules.stream().map(MazeRule::getRuleType).toList());
        assertEquals("active rule", rules.get(0).getDescription());
    }

    @Test
    void discoversEveryRqFileUnderRulesRecursively() throws Exception {
        Path scenario = tempDir.resolve("scenario");
        Files.createDirectories(scenario.resolve("rules/core"));
        Files.createDirectories(scenario.resolve("rules/custom/nested"));
        Files.createDirectories(scenario.resolve("rules-disabled"));
        writeRule(scenario.resolve("rules/root.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");
        writeRule(scenario.resolve("rules/core/active.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");
        writeRule(scenario.resolve("rules/custom/nested/also-active.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");
        writeRule(scenario.resolve("rules-disabled/inactive.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");

        List<String> discovered = new MazeRuleLoader().discoverPackageRuleFiles(scenario).stream()
                .map(path -> scenario.relativize(path).toString().replace('\\', '/'))
                .toList();

        assertEquals(List.of(
                "rules/core/active.rq",
                "rules/custom/nested/also-active.rq",
                "rules/root.rq"),
                discovered);
    }

    @Test
    void ordersRulesByPortableCaseFoldedNames() throws Exception {
        Path scenario = tempDir.resolve("case-order");
        Path zeta = writeRule(scenario.resolve("rules/Zeta.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");
        Path beta = writeRule(scenario.resolve("rules/Nested/beta.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");
        Path alpha = writeRule(scenario.resolve("rules/alpha.rq"), "CONSTRUCT WHERE { ?s ?p ?o }\n");

        MazeRuleLoader loader = new MazeRuleLoader();
        List<MazeRule> rules = loader.loadRulesFromPaths(
                List.of(zeta, beta, alpha),
                scenario,
                List.of());

        assertEquals(List.of(
                "rules/alpha",
                "rules/Nested/beta",
                "rules/Zeta"),
                rules.stream().map(MazeRule::getName).toList());
    }

    private Path writeRule(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }
}
