package org.mase.creator.trig;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MazeTrigParser {

    private static final Pattern GRAPH_PATTERN = Pattern.compile("<([^>]+)>\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern START_PATTERN = Pattern.compile("xhv:start\\s+(<[^>]+>)");
    private static final Pattern EXIT_PATTERN = Pattern.compile("maze:exit\\s+(<[^>]+>)");
    private static final Pattern GREEN_PATTERN = Pattern.compile("maze:green\\s+(<[^>]+>)");
    private static final Pattern CORRECT_PLAN_CELL_PATTERN = Pattern.compile("^#\\s+https?://\\S*/cells/(\\d+)/(\\d+)\\s*$");

    public MazeModel parse(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    public MazeModel parse(String trig) {
        MazeModel model = MazeModel.blank(1, 1);
        List<PendingConnection> pendingConnections = new ArrayList<>();
        List<PendingConnection> pendingRouteSuccessors = new ArrayList<>();
        CellCoordinate parsedExitSource = null;

        Matcher graphMatcher = GRAPH_PATTERN.matcher(trig);
        while (graphMatcher.find()) {
            String graphName = graphMatcher.group(1);
            String graphToken = "<" + graphName + ">";
            Optional<CellCoordinate> coordinate = CellCoordinate.parse(graphToken);
            if (coordinate.isEmpty()) {
                continue;
            }

            String body = graphMatcher.group(2);
            if (!body.contains("maze:Cell")) {
                continue;
            }

            CellCoordinate source = coordinate.get();
            model.createCell(source);

            for (Direction direction : Direction.values()) {
                parseDirectionTarget(body, direction)
                        .flatMap(CellCoordinate::parse)
                        .ifPresent(target -> pendingConnections.add(new PendingConnection(source, target)));
            }

            if (isExitReference(body)) {
                parsedExitSource = source;
            }

            parseGreenTarget(body)
                    .flatMap(CellCoordinate::parse)
                    .ifPresent(target -> pendingRouteSuccessors.add(new PendingConnection(source, target)));
        }

        for (PendingConnection connection : pendingConnections) {
            model.connectIfAdjacent(connection.source(), connection.target());
        }

        parseStart(trig).ifPresent(model::setStartFromParser);
        if (parsedExitSource != null) {
            model.setExitFromParser(parsedExitSource);
        }
        model.setOptimalRouteFromParser(parseCorrectPlan(trig, model));
        model.setGreenRouteFromParser(reconstructRouteFromGreenSuccessors(pendingRouteSuccessors, model));

        return model;
    }

    private Optional<String> parseDirectionTarget(String body, Direction direction) {
        Pattern pattern = Pattern.compile(Pattern.quote(direction.predicate()) + "\\s+((?:maze:Wall)|<[^>]+>)");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<String> parseGreenTarget(String body) {
        Matcher matcher = GREEN_PATTERN.matcher(body);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<CellCoordinate> parseStart(String trig) {
        Matcher matcher = START_PATTERN.matcher(trig);
        if (matcher.find()) {
            return CellCoordinate.parse(matcher.group(1));
        }
        return Optional.empty();
    }

    private boolean isExitReference(String body) {
        Matcher matcher = EXIT_PATTERN.matcher(body);
        if (!matcher.find()) {
            return false;
        }
        return matcher.group(1).contains("/cells/999");
    }

    private List<CellCoordinate> parseCorrectPlan(String trig, MazeModel model) {
        int markerIndex = trig.indexOf("#Correct plan");
        if (markerIndex < 0) {
            return List.of();
        }

        List<CellCoordinate> route = new ArrayList<>();
        String[] lines = trig.substring(markerIndex).split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("#")) {
                break;
            }

            Matcher matcher = CORRECT_PLAN_CELL_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }

            CellCoordinate coordinate = new CellCoordinate(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
            if (model.hasCell(coordinate) && !route.contains(coordinate)) {
                route.add(coordinate);
            }
        }
        return route;
    }

    private List<CellCoordinate> reconstructRouteFromGreenSuccessors(
            List<PendingConnection> pendingRouteSuccessors,
            MazeModel model
    ) {
        if (pendingRouteSuccessors.isEmpty()) {
            return List.of();
        }

        java.util.Map<CellCoordinate, CellCoordinate> successors = new java.util.HashMap<>();
        java.util.Set<CellCoordinate> targets = new java.util.HashSet<>();
        for (PendingConnection connection : pendingRouteSuccessors) {
            if (model.hasCell(connection.source()) && model.hasCell(connection.target())) {
                successors.putIfAbsent(connection.source(), connection.target());
                targets.add(connection.target());
            }
        }

        Optional<CellCoordinate> start = successors.keySet().stream()
                .filter(source -> !targets.contains(source))
                .min(CellCoordinate::compareTo)
                .or(() -> successors.keySet().stream().min(CellCoordinate::compareTo));
        if (start.isEmpty()) {
            return List.of();
        }

        List<CellCoordinate> route = new ArrayList<>();
        java.util.Set<CellCoordinate> seen = new java.util.HashSet<>();
        CellCoordinate current = start.get();
        while (current != null && model.hasCell(current) && seen.add(current)) {
            route.add(current);
            current = successors.get(current);
        }

        return route;
    }

    private record PendingConnection(CellCoordinate source, CellCoordinate target) {
    }
}
