package org.mase.creator.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MazeCell {

    private final CellCoordinate coordinate;
    private final EnumMap<Direction, CellCoordinate> connections = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, String> preservedDirectionTargets = new EnumMap<>(Direction.class);
    private String customTypeSuffix = "";
    private final List<String> customPredicateSegments = new ArrayList<>();
    private String customGraphTail = "";
    private String trailingGraphComment = "";

    public MazeCell(CellCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    public CellCoordinate coordinate() {
        return coordinate;
    }

    public Optional<CellCoordinate> connection(Direction direction) {
        return Optional.ofNullable(connections.get(direction));
    }

    public Map<Direction, CellCoordinate> connections() {
        return Map.copyOf(connections);
    }

    public Optional<String> preservedDirectionTarget(Direction direction) {
        return Optional.ofNullable(preservedDirectionTargets.get(direction));
    }

    public void setPreservedDirectionTarget(Direction direction, String target) {
        if (target == null || target.isBlank() || "maze:Wall".equals(target.strip())) {
            preservedDirectionTargets.remove(direction);
        } else {
            preservedDirectionTargets.put(direction, target.strip());
        }
    }

    public String customTypeSuffix() {
        return customTypeSuffix;
    }

    public List<String> customPredicateSegments() {
        return List.copyOf(customPredicateSegments);
    }

    public String customGraphTail() {
        return customGraphTail;
    }

    public String trailingGraphComment() {
        return trailingGraphComment;
    }

    public boolean hasCustomContent() {
        return !customTypeSuffix.isBlank()
                || !customPredicateSegments.isEmpty()
                || !customGraphTail.isBlank()
                || !trailingGraphComment.isBlank()
                || !preservedDirectionTargets.isEmpty();
    }

    public void setCustomContent(
            String customTypeSuffix,
            List<String> customPredicateSegments,
            String customGraphTail,
            String trailingGraphComment
    ) {
        this.customTypeSuffix = customTypeSuffix == null ? "" : customTypeSuffix.stripTrailing();
        this.customPredicateSegments.clear();
        for (String segment : customPredicateSegments) {
            if (segment != null && !segment.isBlank()) {
                this.customPredicateSegments.add(segment.strip());
            }
        }
        this.customGraphTail = customGraphTail == null ? "" : customGraphTail.stripTrailing();
        this.trailingGraphComment = trailingGraphComment == null ? "" : trailingGraphComment.strip();
    }

    boolean connect(Direction direction, CellCoordinate target) {
        CellCoordinate existing = connections.put(direction, target);
        preservedDirectionTargets.remove(direction);
        return !target.equals(existing);
    }

    boolean disconnect(Direction direction) {
        boolean changed = connections.remove(direction) != null;
        changed |= preservedDirectionTargets.remove(direction) != null;
        return changed;
    }

    boolean disconnectTarget(CellCoordinate target) {
        boolean changed = false;
        for (Direction direction : Direction.values()) {
            if (target.equals(connections.get(direction))) {
                connections.remove(direction);
                changed = true;
            }
        }
        return changed;
    }
}
