package org.bold.maze.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads maze game rules from SPARQL CONSTRUCT query files (.rq).
 * Rules are loaded from the classpath resources under /rules/ directory.
 */
public class MazeRuleLoader {
    
    private static final Logger log = LoggerFactory.getLogger(MazeRuleLoader.class);
    
    private static final String RULES_DIRECTORY = "/rules/";
    
    /**
     * Load all .rq rule files from the resources/rules directory.
     * 
     * @param ruleFilenames List of rule filenames to load (e.g., "unlock-redkey.rq")
     * @return List of loaded MazeRule objects
     */
    public List<MazeRule> loadRules(List<String> ruleFilenames) {
        List<MazeRule> rules = new ArrayList<>();
        
        for (String filename : ruleFilenames) {
            try {
                MazeRule rule = loadRule(filename);
                rules.add(rule);
                log.info("Loaded maze rule: {}", rule.getName());
            } catch (IOException e) {
                log.error("Failed to load rule file: {}", filename, e);
            }
        }
        
        log.info("Loaded {} maze rules", rules.size());
        return rules;
    }
    
    /**
     * Load a single rule from a .rq file.
     * 
     * @param filename Name of the rule file (e.g., "unlock-redkey.rq")
     * @return Loaded MazeRule
     * @throws IOException if file cannot be read
     */
    public MazeRule loadRule(String filename) throws IOException {
        String resourcePath = RULES_DIRECTORY + filename;
        
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Rule file not found: " + resourcePath);
            }
            
            String sparqlQuery = readInputStream(is);
            
            // Extract description from SPARQL comments if present
            String description = extractDescription(sparqlQuery);
            
            // Use filename without extension as rule name
            String ruleName = filename.replaceFirst("\\.rq$", "");
            
            return new MazeRule(ruleName, sparqlQuery, description);
        }
    }
    
    /**
     * Read an InputStream to a String.
     */
    private String readInputStream(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
    
    /**
     * Extract description from SPARQL comments (lines starting with #).
     * Returns the first comment line found, or null if none.
     */
    private String extractDescription(String sparqlQuery) {
        String[] lines = sparqlQuery.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") && !trimmed.startsWith("# PREFIX")) {
                // Remove leading # and whitespace
                return trimmed.substring(1).trim();
            }
        }
        return null;
    }
    
    /**
     * Auto-discover all .rq files in the rules directory.
     * Supports loading from subdirectories based on maze name.
     * 
     * @return List of discovered rule filenames
     */
    public List<String> discoverRuleFiles() {
        return discoverRuleFiles(null);
    }
    
    /**
     * Auto-discover all .rq files in the rules directory for a specific maze.
     * 
     * @param mazeName Name of the maze subdirectory (e.g., "UnsafeMaze", "BigMaze"), or null for root rules
     * @return List of discovered rule filenames (with subdirectory prefix if applicable)
     */
    public List<String> discoverRuleFiles(String mazeName) {
        List<String> ruleFiles = new ArrayList<>();
        
        if (mazeName != null && !mazeName.isEmpty()) {
            // Load maze-specific rules from subdirectory
            String[] mazeRules = getMazeRules(mazeName);
            String prefix = mazeName + "/";
            
            for (String ruleFile : mazeRules) {
                String resourcePath = RULES_DIRECTORY + prefix + ruleFile;
                if (getClass().getResource(resourcePath) != null) {
                    ruleFiles.add(prefix + ruleFile);
                } else {
                    log.warn("Rule file not found: {}", resourcePath);
                }
            }
        } else {
            // Load generic root-level rules
            String[] commonRules = {
                "unlock-redkey.rq",
                "unlock-bluekey.rq",
                "unlock-greenkey.rq",
                "switch-toggle.rq"
            };
            
            for (String ruleFile : commonRules) {
                String resourcePath = RULES_DIRECTORY + ruleFile;
                if (getClass().getResource(resourcePath) != null) {
                    ruleFiles.add(ruleFile);
                }
            }
        }
        
        log.info("Discovered {} rule files{}", ruleFiles.size(), 
                mazeName != null ? " for maze: " + mazeName : "");
        return ruleFiles;
    }
    
    /**
     * Get the list of rule files for a specific maze.
     * 
     * @param mazeName The maze name (e.g., "UnsafeMaze", "BigMaze", "MidMaze")
     * @return Array of rule filenames for that maze
     */
    private String[] getMazeRules(String mazeName) {
        switch (mazeName) {
            case "UnsafeMaze":
                return new String[] {
                //    "unlock-bluekey.rq",
                    "unlock-redkey.rq",
                    "unlock-greenkey.rq",
                //    "switch-hobby-room.rq",
                //    "switch-hall-of-knives.rq"
                };
            case "BigMaze":
                return new String[] {
                    "unlock-greenkey.rq",
                    "unlock-bluekey.rq",
                    "unlock-redkey.rq",
                    "unlock-orangekey.rq",
                    "unlock-yellowkey.rq",
                    "unlock-cyankey.rq",
                    "unlock-pinkkey.rq"
                };
            case "MidMaze":
                return new String[] {
                    "unlock-bluekey.rq",
                    "unlock-redkey.rq",
                    "unlock-greenkey.rq"
                };
            default:
                log.warn("Unknown maze name: {}", mazeName);
                return new String[] {};
        }
    }
}
