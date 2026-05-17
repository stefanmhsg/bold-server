package org.maze.infrastructure.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves and validates a self-contained scenario package directory.
 */
public class ScenarioPackageResolver {

    public static final String SCENARIO_PROPERTIES = "scenario.properties";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String INIT_DATASET_KEY = "mase.init.dataset";
    public static final String SCENARIO_ID_KEY = "mase.scenario.id";
    private static final String RULES_DIRECTORY = "rules";

    public ScenarioPackage resolve(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IOException("Scenario package root is not a directory: " + normalizedRoot);
        }

        Path propertiesFile = normalizedRoot.resolve(SCENARIO_PROPERTIES);
        if (!Files.isRegularFile(propertiesFile)) {
            throw new IOException("Scenario package is missing " + SCENARIO_PROPERTIES + ": " + propertiesFile);
        }

        Properties properties = loadProperties(propertiesFile);
        String scenarioId = properties.getProperty(SCENARIO_ID_KEY);
        if (scenarioId == null || scenarioId.isBlank()) {
            scenarioId = normalizedRoot.getFileName().toString();
        } else {
            scenarioId = scenarioId.trim();
        }

        String datasetPattern = requireProperty(properties, INIT_DATASET_KEY, propertiesFile);
        String resolvedDatasetPattern = resolvePatternString(normalizedRoot, datasetPattern);
        List<Path> dataFiles = resolveFiles(normalizedRoot, datasetPattern);
        if (dataFiles.isEmpty()) {
            throw new IOException("Scenario package dataset pattern matched no files: "
                    + datasetPattern + " under " + normalizedRoot);
        }

        List<Path> ruleFiles = resolveRuleFiles(normalizedRoot);
        if (ruleFiles.isEmpty()) {
            throw new IOException("Scenario package has no active .rq files under "
                    + RULES_DIRECTORY + "/ in " + normalizedRoot);
        }

        Path manifest = normalizedRoot.resolve(MANIFEST_JSON);
        Path validation = normalizedRoot.resolve("validation");
        Path agents = normalizedRoot.resolve("agents");

        return new ScenarioPackage(
                normalizedRoot,
                scenarioId,
                propertiesFile,
                Files.isRegularFile(manifest) ? Optional.of(manifest) : Optional.empty(),
                resolvedDatasetPattern,
                dataFiles,
                ruleFiles,
                discoverFilesByExtension(validation, ".rq"),
                Files.isDirectory(agents) ? Optional.of(agents) : Optional.empty());
    }

    private Properties loadProperties(Path propertiesFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }
        return properties;
    }

    private String requireProperty(Properties properties, String key, Path propertiesFile) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Scenario package property '" + key + "' is required in " + propertiesFile);
        }
        return value.trim();
    }

    private List<Path> resolveRuleFiles(Path root) throws IOException {
        Path activeRuleRoot = root.resolve(RULES_DIRECTORY).toAbsolutePath().normalize();
        if (!Files.isDirectory(activeRuleRoot)) {
            throw new IOException("Scenario package active rules directory is missing: " + activeRuleRoot);
        }
        return discoverFilesByExtension(activeRuleRoot, ".rq");
    }

    private List<Path> discoverFilesByExtension(Path directory, String extension) throws IOException {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(normalizedDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(portablePathComparator(normalizedDirectory))
                    .toList();
        }
    }

    private List<Path> resolveFiles(Path root, String pattern) throws IOException {
        Path patternPath = Paths.get(pattern);
        if (patternPath.isAbsolute()) {
            throw new IOException("Scenario package paths must be relative, got: " + pattern);
        }

        if (!hasWildcard(pattern)) {
            Path resolved = root.resolve(pattern).toAbsolutePath().normalize();
            ensureInsideRoot(root, resolved, "dataset path");
            return Files.isRegularFile(resolved) ? List.of(resolved) : List.of();
        }

        Path baseDirectory = activeRootForPattern(root, pattern);
        if (!Files.isDirectory(baseDirectory)) {
            return List.of();
        }

        Pattern regex = Pattern.compile(globToRegex(normalizePattern(pattern)));
        try (Stream<Path> stream = Files.walk(baseDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> regex.matcher(root.relativize(path).toString().replace('\\', '/')).matches())
                    .sorted(portablePathComparator(root))
                    .toList();
        }
    }

    private String resolvePatternString(Path root, String pattern) throws IOException {
        Path patternPath = Paths.get(pattern);
        if (patternPath.isAbsolute()) {
            throw new IOException("Scenario package paths must be relative, got: " + pattern);
        }
        Path resolved = root.resolve(pattern).toAbsolutePath().normalize();
        Path guard = hasWildcard(pattern) ? activeRootForPattern(root, pattern) : resolved;
        ensureInsideRoot(root, guard, "dataset pattern");
        return resolved.toString();
    }

    private Path activeRootForPattern(Path root, String pattern) throws IOException {
        String normalized = normalizePattern(pattern);
        int wildcardIndex = firstWildcardIndex(normalized);
        String base = normalized;
        if (wildcardIndex >= 0) {
            int slashBeforeWildcard = normalized.lastIndexOf('/', wildcardIndex);
            base = slashBeforeWildcard >= 0 ? normalized.substring(0, slashBeforeWildcard) : ".";
        }
        if (base.isBlank()) {
            base = ".";
        }
        Path relative = Paths.get(base);
        if (relative.isAbsolute()) {
            throw new IOException("Scenario package paths must be relative, got: " + pattern);
        }
        Path resolved = root.resolve(relative).toAbsolutePath().normalize();
        ensureInsideRoot(root, resolved, "package path");
        return resolved;
    }

    private void ensureInsideRoot(Path root, Path candidate, String label) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IOException("Scenario package " + label + " escapes package root: " + candidate);
        }
    }

    private boolean hasWildcard(String pattern) {
        return firstWildcardIndex(normalizePattern(pattern)) >= 0;
    }

    private int firstWildcardIndex(String pattern) {
        int star = pattern.indexOf('*');
        int question = pattern.indexOf('?');
        if (star < 0) {
            return question;
        }
        if (question < 0) {
            return star;
        }
        return Math.min(star, question);
    }

    private String normalizePattern(String pattern) {
        return pattern.trim().replace('\\', '/');
    }

    private Comparator<Path> portablePathComparator(Path root) {
        return Comparator
                .comparing((Path path) -> portableRelativePath(root, path).toLowerCase(Locale.ROOT))
                .thenComparing(path -> portableRelativePath(root, path));
    }

    private String portableRelativePath(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedRoot.relativize(normalizedPath).toString().replace('\\', '/');
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?");
                        i += 2;
                    } else {
                        regex.append(".*");
                        i++;
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (".()[]{}+$^|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else if (c == '/') {
                regex.append('/');
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return regex.toString();
    }
}
