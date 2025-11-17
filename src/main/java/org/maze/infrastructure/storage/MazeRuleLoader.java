package org.maze.infrastructure.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.maze.domain.rules.MazeRule;
import org.maze.infrastructure.io.FileUtils;
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
            
            // Auto-detect rule type based on query content
            MazeRule.RuleType ruleType = detectRuleType(sparqlQuery);
            
            log.debug("Loaded rule '{}' as type: {}", ruleName, ruleType);
            
            return new MazeRule(ruleName, sparqlQuery, description, ruleType);
        }
    }
    
    /**
     * Detect the rule type by analyzing the SPARQL query content.
     * 
     * UPDATE rules contain DELETE/INSERT/WHERE keywords.
     * CONSTRUCT rules contain CONSTRUCT/WHERE keywords.
     * 
     * @param sparqlQuery The SPARQL query text
     * @return Detected rule type (defaults to CONSTRUCT if ambiguous)
     */
    private MazeRule.RuleType detectRuleType(String sparqlQuery) {
        String normalized = sparqlQuery.toUpperCase();
        
        // Check for UPDATE operations (DELETE, INSERT without CONSTRUCT)
        boolean hasDelete = normalized.contains("DELETE");
        boolean hasInsert = normalized.contains("INSERT");
        boolean hasConstruct = normalized.contains("CONSTRUCT");
        
        // If has DELETE or INSERT but NOT CONSTRUCT, it's an UPDATE rule
        if ((hasDelete || hasInsert) && !hasConstruct) {
            return MazeRule.RuleType.UPDATE;
        }
        
        // Otherwise default to CONSTRUCT (includes pure CONSTRUCT queries)
        return MazeRule.RuleType.CONSTRUCT;
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
     * Auto-discover all .rq files in the rules directory for a specific maze or directory path.
     * Dynamically discovers all .rq files in the specified subdirectory.
     * Supports nested paths like "Global/Stigmergy".
     * 
     * @param pathName Name of the subdirectory path (e.g., "UnsafeMaze", "Global/Stigmergy"), or null for root rules
     * @return List of discovered rule filenames (with subdirectory prefix if applicable)
     */
    public List<String> discoverRuleFiles(String pathName) {
        List<String> ruleFiles = new ArrayList<>();
        
        try {
            if (pathName != null && !pathName.isEmpty()) {
                // Load rules from specified subdirectory path (supports nested paths)
                String subDirPath = RULES_DIRECTORY + pathName + "/";
                ruleFiles = discoverRuleFilesInResource(subDirPath, pathName + "/");
            } else {
                // Load generic root-level rules (no subdirectory)
                ruleFiles = discoverRuleFilesInResource(RULES_DIRECTORY, "");
            }
        } catch (Exception e) {
            log.error("Error discovering rule files for path: {}", pathName, e);
        }
        
        log.info("Discovered {} rule files{}", ruleFiles.size(), 
                pathName != null ? " for path: " + pathName : "");
        return ruleFiles;
    }
    
    /**
     * Discover all .rq files in a resource directory using FileUtils.
     * Since the server runs from file system (not JAR), we can use simple file pattern matching.
     * 
     * @param resourcePath The resource path to search (e.g., "/rules/" or "/rules/UnsafeMaze/")
     * @param prefix The prefix to add to discovered filenames (e.g., "" or "UnsafeMaze/")
     * @return List of rule filenames with prefix
     */
    private List<String> discoverRuleFilesInResource(String resourcePath, String prefix) {
        List<String> ruleFiles = new ArrayList<>();
        
        try {
            // Convert resource path to file system path pattern
            // E.g., "/rules/UnsafeMaze/" -> "src/main/resources/rules/UnsafeMaze/*.rq"
            String fileSystemPath = "src/main/resources" + resourcePath + "*.rq";
            
            // Use FileUtils to discover all .rq files
            Set<String> discoveredFiles = FileUtils.listFiles(fileSystemPath);
            
            // Extract just the filename and add prefix
            for (String absolutePath : discoveredFiles) {
                String fileName = Paths.get(absolutePath).getFileName().toString();
                ruleFiles.add(prefix + fileName);
                log.debug("Discovered rule file: {}{}", prefix, fileName);
            }
            
        } catch (IOException e) {
            log.error("Error discovering rule files in: {}", resourcePath, e);
        }
        
        return ruleFiles;
    }
}
