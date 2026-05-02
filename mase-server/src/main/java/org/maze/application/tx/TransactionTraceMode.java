package org.maze.application.tx;

/**
 * Scenario-level tracing policy. Summary mode keeps the semantic transaction
 * header visible without snapshotting RDF triples around every rule execution.
 */
public enum TransactionTraceMode {
    OFF,
    SUMMARY,
    FULL;

    public boolean emitsEvents() {
        return this != OFF;
    }

    public boolean capturesTriples() {
        return this == FULL;
    }

    public String wireValue() {
        return name().toLowerCase();
    }

    public static TransactionTraceMode fromProperty(String value) {
        if (value == null || value.isBlank()) {
            return SUMMARY;
        }

        return switch (value.trim().toLowerCase()) {
            case "false", "off", "none", "disabled" -> OFF;
            case "true", "full", "triples", "debug" -> FULL;
            case "summary", "headers", "header", "light" -> SUMMARY;
            default -> throw new IllegalArgumentException(
                    "Unsupported mase.transaction.trace value: " + value
                            + ". Use off, summary, or full.");
        };
    }
}
