package org.maze.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.maze.application.tx.TransactionTraceMode;
import org.maze.infrastructure.scenario.ScenarioPackage;
import org.maze.infrastructure.scenario.ScenarioPackageResolver;

/**
 * Server configuration loaded from properties files.
 * Provides type-safe access to configuration values.
 */
public class ServerConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ServerConfiguration.class);
    
    // TODO
    private static final String SERVER_HTTP_PORT_KEY = "mase.server.httpPort";
    private static final String SERVER_HTTP_PORT_DEFAULT = "8080";
    private static final String TRANSACTION_TRACE_KEY = "mase.transaction.trace";
    private static final String TRANSACTION_TRACE_DEFAULT = "summary";
    
    private final Properties properties;
    private final ScenarioPackage scenarioPackage;
    
    private ServerConfiguration(ScenarioPackage scenarioPackage) throws IOException {
        this.properties = loadProperties(scenarioPackage.propertiesFile());
        this.scenarioPackage = scenarioPackage;

        log.info("Loading scenario package configuration from: {}", scenarioPackage.propertiesFile());
    }

    public static ServerConfiguration forScenarioPackage(Path scenarioRoot) throws IOException {
        ScenarioPackage scenarioPackage = new ScenarioPackageResolver().resolve(scenarioRoot);
        return new ServerConfiguration(scenarioPackage);
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties loaded = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            loaded.load(input);
        }
        return loaded;
    }
    
    public int getPort() {
        return Integer.parseInt(properties.getProperty(SERVER_HTTP_PORT_KEY, SERVER_HTTP_PORT_DEFAULT));
    }
    
    public String getInitDataset() {
        return scenarioPackage.resolvedDatasetPattern();
    }

    public TransactionTraceMode getTransactionTraceMode() {
        return TransactionTraceMode.fromProperty(properties.getProperty(
                TRANSACTION_TRACE_KEY,
                TRANSACTION_TRACE_DEFAULT));
    }
    
    public String getTaskName() {
        return scenarioPackage.id();
    }

    public boolean isScenarioPackageMode() {
        return true;
    }

    public Optional<ScenarioPackage> getScenarioPackage() {
        return Optional.ofNullable(scenarioPackage);
    }
    
    public Properties getRawProperties() {
        return properties;
    }
    
    /**
     * Get the rule execution order patterns.
     * Returns a list of wildcard patterns that define the order in which rules should execute.
     * Patterns support wildcards: * (any characters) and ? (single character)
     * 
     * Example: "unlock*, stigmergy*, move*" will execute unlock rules first, then stigmergy, then move
     * 
     * @return List of patterns, or empty list if not configured
     */
    public List<String> getRuleExecutionOrder() {
        String orderValue = properties.getProperty("mase.rules.execution.order");
        
        if (orderValue == null || orderValue.trim().isEmpty()) {
            log.debug("No rule execution order configured");
            return List.of();
        }
        
        List<String> patterns = Arrays.stream(orderValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        
        log.info("Rule execution order patterns: {}", patterns);
        return patterns;
    }
}
