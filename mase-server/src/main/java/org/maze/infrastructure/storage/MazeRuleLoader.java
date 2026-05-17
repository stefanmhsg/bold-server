package org.maze.infrastructure.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.maze.domain.rules.MazeRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads scenario-package SPARQL rule files.
 */
public class MazeRuleLoader {
    
    private static final Logger log = LoggerFactory.getLogger(MazeRuleLoader.class);

    public List<MazeRule> loadRulesFromPaths(List<Path> ruleFiles, Path ruleNameRoot, List<String> orderPatterns) {
        List<MazeRule> rules = new ArrayList<>();

        for (Path ruleFile : ruleFiles) {
            try {
                MazeRule rule = loadRule(ruleFile, ruleNameRoot);
                rules.add(rule);
                log.info("Loaded maze rule: {}", rule.getName());
            } catch (IOException e) {
                log.error("Failed to load rule file: {}", ruleFile, e);
            }
        }

        log.info("Loaded {} maze rules from package paths (before ordering)", rules.size());

        List<MazeRule> orderedRules = applyRuleOrdering(rules, orderPatterns);

        log.info("Final rule execution order: {}",
                orderedRules.stream()
                        .map(MazeRule::getName)
                        .collect(Collectors.joining(", ")));

        return orderedRules;
    }

    public MazeRule loadRule(Path ruleFile, Path ruleNameRoot) throws IOException {
        Path normalizedRuleFile = ruleFile.toAbsolutePath().normalize();
        Path normalizedRoot = ruleNameRoot.toAbsolutePath().normalize();
        if (!normalizedRuleFile.startsWith(normalizedRoot)) {
            throw new IOException("Rule file is outside rule name root: " + normalizedRuleFile);
        }

        String sparqlQuery = Files.readString(normalizedRuleFile, StandardCharsets.UTF_8);
        String description = extractDescription(sparqlQuery);
        String ruleName = normalizedRoot.relativize(normalizedRuleFile)
                .toString()
                .replace('\\', '/')
                .replaceFirst("\\.rq$", "");
        MazeRule.RuleType ruleType = detectRuleType(sparqlQuery);

        log.debug("Loaded rule '{}' as type: {}", ruleName, ruleType);

        return new MazeRule(ruleName, sparqlQuery, description, ruleType);
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

    public List<Path> discoverPackageRuleFiles(Path scenarioRoot) throws IOException {
        Path rulesRoot = scenarioRoot.toAbsolutePath().normalize().resolve("rules");
        if (!Files.isDirectory(rulesRoot)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(rulesRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".rq"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
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
