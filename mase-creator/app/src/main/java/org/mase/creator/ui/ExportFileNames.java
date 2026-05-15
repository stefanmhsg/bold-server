package org.mase.creator.ui;

import java.nio.file.Files;
import java.nio.file.Path;

final class ExportFileNames {

    private static final String TRIG_EXTENSION = ".trig";

    private ExportFileNames() {
    }

    static String normalizeTrigFileName(String input) {
        String fileName = input == null ? "" : input.strip();
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be empty.");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("Enter a file name, not a path.");
        }
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(TRIG_EXTENSION)) {
            fileName += TRIG_EXTENSION;
        }
        return fileName;
    }

    static String nextAvailableFileName(Path outputDirectory, String preferredFileName) {
        String normalized = normalizeTrigFileName(preferredFileName);
        Path preferred = outputDirectory.resolve(normalized);
        if (!Files.exists(preferred)) {
            return normalized;
        }

        String baseName = normalized.substring(0, normalized.length() - TRIG_EXTENSION.length());
        for (int index = 1; index < 10_000; index++) {
            String candidate = baseName + "-" + index + TRIG_EXTENSION;
            if (!Files.exists(outputDirectory.resolve(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find an available file name.");
    }
}
