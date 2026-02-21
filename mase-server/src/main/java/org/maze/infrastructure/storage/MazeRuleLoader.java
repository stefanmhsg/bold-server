package org.maze.infrastructure.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
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
     * Rules are loaded in the order specified by orderPatterns (if provided).
     * 
     * @param ruleFilenames List of rule filenames to load (e.g., "unlock-redkey.rq")
     * @param orderPatterns List of wildcard patterns defining execution order (can be null/empty)
     * @return List of loaded MazeRule objects, ordered according to patterns
     */
    public List<MazeRule> loadRules(List<String> ruleFilenames, List<String> orderPatterns) {
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
        
        log.info("Loaded {} maze rules (before ordering)", rules.size());
        
        // Apply ordering from configuration
        List<MazeRule> orderedRules = applyRuleOrdering(rules, orderPatterns);
        
        log.info("Final rule execution order: {}", 
                orderedRules.stream()
                           .map(MazeRule::getName)
                           .collect(Collectors.joining(", ")));
        
        return orderedRules;
    }
    
    /**
     * Load all .rq rule files from the resources/rules directory.
     * Legacy method - loads rules without ordering.
     * 
     * @param ruleFilenames List of rule filenames to load (e.g., "unlock-redkey.rq")
     * @return List of loaded MazeRule objects in alphabetical order
     */
    public List<MazeRule> loadRules(List<String> ruleFilenames) {
        return loadRules(ruleFilenames, null);
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
     * @param pathName Name of the subdirectory path (e.g., "SmallMaze", "Global/Stigmergy"), or null for root rules
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
     * @param resourcePath The resource path to search (e.g., "/rules/" or "/rules/SmallMaze/")
     * @param prefix The prefix to add to discovered filenames (e.g., "" or "SmallMaze/")
     * @return List of rule filenames with prefix
     */
    private List<String> discoverRuleFilesInResource(String resourcePath, String prefix) {
        List<String> ruleFiles = new ArrayList<>();
        
        try {
            // Convert resource path to file system path pattern
            // E.g., "/rules/SmallMaze/" -> "src/main/resources/rules/SmallMaze/*.rq"
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
    
    // ==================== Rule Ordering ====================
    
    /**
     * Apply ordering to loaded rules based on patterns from configuration.
     * Rules are sorted according to pattern matching order.
     * Rules that don't match any pattern are placed at the end in alphabetical order.
     * 
     * @param rules List of loaded rules (unordered)
     * @param patterns List of wildcard patterns (can be null/empty)
     * @return List of rules in execution order
     */
    private List<MazeRule> applyRuleOrdering(List<MazeRule> rules, List<String> patterns) {
        // If no patterns defined, return rules in alphabetical order by name
        if (patterns == null || patterns.isEmpty()) {
            log.info("No rule execution order configured, using alphabetical order");
            rules.sort(Comparator.comparing(MazeRule::getName));
            return rules;
        }
        
        log.info("Applying rule execution order patterns: {}", patterns);
        
        // Build ordered list by matching rules to patterns
        List<MazeRule> orderedRules = new ArrayList<>();
        Set<MazeRule> matched = new HashSet<>();
        
        for (String pattern : patterns) {
            Pattern regex = wildcardToRegex(pattern);
            List<MazeRule> matchingRules = new ArrayList<>();
            
            for (MazeRule rule : rules) {
                if (!matched.contains(rule)) {
                    // Extract simple name (last component after /) for matching
                    String simpleName = rule.getName();
                    int lastSlash = simpleName.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        simpleName = simpleName.substring(lastSlash + 1);
                    }
                    
                    if (regex.matcher(simpleName).matches()) {
                        matchingRules.add(rule);
                        matched.add(rule);
                    }
                }
            }
            
            if (matchingRules.isEmpty()) {
                log.debug("Pattern '{}' matched no rules (skipping)", pattern);
            } else {
                // Sort matching rules alphabetically within the same pattern
                matchingRules.sort(Comparator.comparing(MazeRule::getName));
                orderedRules.addAll(matchingRules);
                
                log.debug("Pattern '{}' matched {} rule(s): {}", 
                         pattern, 
                         matchingRules.size(),
                         matchingRules.stream()
                                     .map(MazeRule::getName)
                                     .collect(Collectors.joining(", ")));
            }
        }
        
        // Add any unmatched rules at the end (sorted alphabetically)
        List<MazeRule> unmatchedRules = new ArrayList<>();
        for (MazeRule rule : rules) {
            if (!matched.contains(rule)) {
                unmatchedRules.add(rule);
            }
        }
        
        if (!unmatchedRules.isEmpty()) {
            unmatchedRules.sort(Comparator.comparing(MazeRule::getName));
            orderedRules.addAll(unmatchedRules);
            
            log.info("Added {} unmatched rule(s) at end: {}",
                    unmatchedRules.size(),
                    unmatchedRules.stream()
                                 .map(MazeRule::getName)
                                 .collect(Collectors.joining(", ")));
        }
        
        return orderedRules;
    }
    
    /**
     * Convert a wildcard pattern to a regex Pattern.
     * Supports:
     * - * matches any characters
     * - ? matches a single character
     * - Literal text matches exactly
     * 
     * Examples:
     * - "unlock*" matches "unlock-redkey", "unlock-bluekey", etc.
     * - "*stigmergy*" matches "pheromone-stigmergy", "stigmergy-update", etc.
     * - "move" matches only "move"
     * 
     * @param wildcardPattern Pattern with wildcards
     * @return Compiled regex Pattern
     */
    private Pattern wildcardToRegex(String wildcardPattern) {
        // Escape special regex characters except * and ?
        String regex = wildcardPattern
            .replace("\\", "\\\\")
            .replace(".", "\\.")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace("+", "\\+")
            .replace("|", "\\|")
            // Convert wildcards to regex
            .replace("*", ".*")
            .replace("?", ".");
        
        // Match entire string (add anchors)
        regex = "^" + regex + "$";
        
        return Pattern.compile(regex);
    }
}
