package org.mase.creator.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ScenarioPackageNames {

    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _.-]*");

    private ScenarioPackageNames() {
    }

    public static String normalizePackageDirectoryName(String input) {
        String name = input == null ? "" : input.strip();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Package name must not be empty.");
        }
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Enter a package folder name, not a path.");
        }
        if (name.endsWith(".") || name.endsWith(" ")) {
            throw new IllegalArgumentException("Package name must not end with a dot or space.");
        }
        if (!PACKAGE_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Use letters, numbers, spaces, dots, underscores, or hyphens.");
        }
        return name;
    }

    public static String nextAvailablePackageName(Path outputDirectory, String preferredName) {
        String normalized = normalizePackageDirectoryName(preferredName);
        if (!Files.exists(outputDirectory.resolve(normalized))) {
            return normalized;
        }

        for (int index = 1; index < 10_000; index++) {
            String candidate = normalized + "-" + index;
            if (!Files.exists(outputDirectory.resolve(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find an available package name.");
    }

    public static String scenarioIdFromPackageName(String packageName) {
        String normalized = normalizePackageDirectoryName(packageName);
        String scenarioId = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return scenarioId.isBlank() ? "masecreator" : scenarioId;
    }

    public static String trigFileNameFromPackageName(String packageName) {
        return normalizePackageDirectoryName(packageName) + ".trig";
    }
}
