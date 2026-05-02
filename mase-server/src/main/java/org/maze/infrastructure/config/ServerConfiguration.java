package org.maze.infrastructure.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server configuration loaded from properties files.
 * Provides type-safe access to configuration values.
 */
public class ServerConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ServerConfiguration.class);
    
    // TODO
    private static final String SERVER_HTTP_PORT_KEY = "mase.server.httpPort";
    private static final String SERVER_HTTP_PORT_DEFAULT = "8080";
    private static final String INIT_DATASET_KEY = "mase.init.dataset";
    private static final String SERVER_PROTOCOL_KEY = "mase.server.protocol";
    private static final String TRANSACTION_TRACE_KEY = "mase.transaction.trace";
    private static final String TRANSACTION_TRACE_DEFAULT = "false";
    
    private final Properties properties;
    private final String taskName;
    
    public ServerConfiguration(String taskName) throws IOException {
        this.taskName = taskName;
        this.properties = new Properties();
        String configFile = taskName + ".properties";
        
        log.info("Loading configuration from: {}", configFile);
        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
        }
    }
    
    public int getPort() {
        return Integer.parseInt(properties.getProperty(SERVER_HTTP_PORT_KEY, SERVER_HTTP_PORT_DEFAULT));
    }
    
    public String getInitDataset() {
        return properties.getProperty(INIT_DATASET_KEY);
    }
    
    public String getServerProtocol() {
        return properties.getProperty(SERVER_PROTOCOL_KEY);
    }

    public boolean isTransactionTraceEnabled() {
        return Boolean.parseBoolean(properties.getProperty(
                TRANSACTION_TRACE_KEY,
                TRANSACTION_TRACE_DEFAULT));
    }
    
    public String getTaskName() {
        return taskName;
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
