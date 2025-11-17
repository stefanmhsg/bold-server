package org.maze.infrastructure.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server configuration loaded from properties files.
 * Provides type-safe access to configuration values.
 */
public class ServerConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ServerConfiguration.class);
    
    private static final String SERVER_HTTP_PORT_KEY = "bold.server.httpPort";
    private static final String SERVER_HTTP_PORT_DEFAULT = "8080";
    private static final String INIT_DATASET_KEY = "bold.init.dataset";
    private static final String SERVER_PROTOCOL_KEY = "bold.server.protocol";
    
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
    
    public String getTaskName() {
        return taskName;
    }
    
    public Properties getRawProperties() {
        return properties;
    }
}
