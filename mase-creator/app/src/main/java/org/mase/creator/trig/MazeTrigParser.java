package org.mase.creator.trig;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeCell;
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

    private static final Pattern GRAPH_PATTERN = Pattern.compile("(?m)^[ \\t]*<([^>]+)>\\s*\\{(.*?)\\}([ \\t]*#[^\\r\\n]*)?", Pattern.DOTALL);
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
            String activeBody = stripComments(body);
            if (!activeBody.contains("maze:Cell")) {
                continue;
            }

            CellCoordinate source = coordinate.get();
            model.createCell(source);
            MazeCell cell = model.cell(source).orElseThrow();
            CustomCellContent customContent = extractCustomCellContent(graphToken, body, graphMatcher.group(3));
            cell.setCustomContent(
                    customContent.typeSuffix(),
                    customContent.predicateSegments(),
                    customContent.graphTail(),
                    customContent.trailingGraphComment()
            );

            for (Direction direction : Direction.values()) {
                parseDirectionTarget(activeBody, direction)
                        .flatMap(CellCoordinate::parse)
                        .ifPresent(target -> pendingConnections.add(new PendingConnection(source, target)));
            }

            if (isExitReference(activeBody)) {
                parsedExitSource = source;
            }

            parseGreenTarget(activeBody)
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
        model.setGreenRoutesFromParser(reconstructRoutesFromGreenSuccessors(pendingRouteSuccessors, model));

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

    private CustomCellContent extractCustomCellContent(
            String graphToken,
            String body,
            String trailingGraphComment
    ) {
        int statementStart = body.indexOf(graphToken);
        if (statementStart < 0) {
            return new CustomCellContent("", List.of(), body, trailingGraphComment);
        }

        int statementEnd = findFirstStatementEnd(body, statementStart);
        if (statementEnd < 0) {
            return new CustomCellContent("", List.of(), "", trailingGraphComment);
        }

        String firstStatement = body.substring(statementStart, statementEnd + 1);
        String graphTail = body.substring(statementEnd + 1);
        return new CustomCellContent(
                extractTypeSuffix(firstStatement),
                extractCustomPredicateSegments(firstStatement),
                graphTail,
                trailingGraphComment
        );
    }

    private int findFirstStatementEnd(String text, int start) {
        boolean inIri = false;
        boolean inComment = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inComment) {
                if (current == '\n' || current == '\r') {
                    inComment = false;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (inIri) {
                if (current == '>') {
                    inIri = false;
                }
                continue;
            }

            if (current == '#') {
                inComment = true;
            } else if (current == '<') {
                inIri = true;
            } else if (current == '"' || current == '\'') {
                quote = current;
            } else if (current == '.') {
                return i;
            }
        }
        return -1;
    }

    private String extractTypeSuffix(String firstStatement) {
        List<String> segments = splitTopLevelSemicolonSegments(stripFinalStatementDot(firstStatement));
        if (segments.isEmpty()) {
            return "";
        }

        String typeSegment = segments.get(0);
        int cellTypeIndex = typeSegment.indexOf("maze:Cell");
        if (cellTypeIndex < 0) {
            return "";
        }
        return typeSegment.substring(cellTypeIndex + "maze:Cell".length()).strip();
    }

    private List<String> extractCustomPredicateSegments(String firstStatement) {
        List<String> segments = splitTopLevelSemicolonSegments(stripFinalStatementDot(firstStatement));
        if (segments.size() < 2) {
            return List.of();
        }

        List<String> customSegments = new ArrayList<>();
        for (int i = 1; i < segments.size(); i++) {
            String segment = segments.get(i).strip();
            if (!segment.isBlank() && !isGeneratedCellPredicateSegment(segment)) {
                customSegments.add(segment);
            }
        }
        return customSegments;
    }

    private String stripFinalStatementDot(String statement) {
        int end = statement.length() - 1;
        while (end >= 0 && Character.isWhitespace(statement.charAt(end))) {
            end--;
        }
        if (end >= 0 && statement.charAt(end) == '.') {
            return statement.substring(0, end);
        }
        return statement;
    }

    private List<String> splitTopLevelSemicolonSegments(String statement) {
        List<String> segments = new ArrayList<>();
        int segmentStart = 0;
        boolean inIri = false;
        char quote = 0;
        boolean escaped = false;
        int bracketDepth = 0;

        for (int i = 0; i < statement.length(); i++) {
            char current = statement.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (inIri) {
                if (current == '>') {
                    inIri = false;
                }
                continue;
            }

            if (current == '<') {
                inIri = true;
            } else if (current == '"' || current == '\'') {
                quote = current;
            } else if (current == '[' || current == '(') {
                bracketDepth++;
            } else if ((current == ']' || current == ')') && bracketDepth > 0) {
                bracketDepth--;
            } else if (current == ';' && bracketDepth == 0) {
                segments.add(statement.substring(segmentStart, i));
                segmentStart = i + 1;
            }
        }

        segments.add(statement.substring(segmentStart));
        return segments;
    }

    private boolean isGeneratedCellPredicateSegment(String segment) {
        return startsWithPredicate(segment, Direction.NORTH.predicate())
                || startsWithPredicate(segment, Direction.WEST.predicate())
                || startsWithPredicate(segment, Direction.SOUTH.predicate())
                || startsWithPredicate(segment, Direction.EAST.predicate())
                || startsWithPredicate(segment, "maze:exit")
                || startsWithPredicate(segment, "maze:green");
    }

    private boolean startsWithPredicate(String segment, String predicate) {
        return segment.equals(predicate)
                || segment.startsWith(predicate + " ")
                || segment.startsWith(predicate + "\t")
                || segment.startsWith(predicate + System.lineSeparator());
    }

    private String stripComments(String text) {
        StringBuilder stripped = new StringBuilder(text.length());
        boolean inIri = false;
        boolean inComment = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inComment) {
                if (current == '\n' || current == '\r') {
                    inComment = false;
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                continue;
            }
            if (quote != 0) {
                stripped.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (inIri) {
                stripped.append(current);
                if (current == '>') {
                    inIri = false;
                }
                continue;
            }

            if (current == '#') {
                inComment = true;
                stripped.append(' ');
            } else {
                stripped.append(current);
                if (current == '<') {
                    inIri = true;
                } else if (current == '"' || current == '\'') {
                    quote = current;
                }
            }
        }
        return stripped.toString();
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

    private List<List<CellCoordinate>> reconstructRoutesFromGreenSuccessors(
            List<PendingConnection> pendingRouteSuccessors,
            MazeModel model
    ) {
        if (pendingRouteSuccessors.isEmpty()) {
            return List.of();
        }

        java.util.Map<CellCoordinate, CellCoordinate> successors = new java.util.TreeMap<>();
        java.util.Set<CellCoordinate> targets = new java.util.HashSet<>();
        for (PendingConnection connection : pendingRouteSuccessors) {
            if (model.hasCell(connection.source())) {
                successors.putIfAbsent(connection.source(), connection.target());
                targets.add(connection.target());
            }
        }

        if (successors.isEmpty()) {
            return List.of();
        }

        List<List<CellCoordinate>> routes = new ArrayList<>();
        java.util.Set<CellCoordinate> consumedSources = new java.util.HashSet<>();
        successors.keySet().stream()
                .filter(source -> !targets.contains(source))
                .forEach(source -> routes.add(followGreenRoute(source, successors, consumedSources)));

        for (CellCoordinate source : successors.keySet()) {
            if (!consumedSources.contains(source)) {
                routes.add(followGreenRoute(source, successors, consumedSources));
            }
        }

        return routes.stream()
                .filter(route -> !route.isEmpty())
                .toList();
    }

    private List<CellCoordinate> followGreenRoute(
            CellCoordinate start,
            java.util.Map<CellCoordinate, CellCoordinate> successors,
            java.util.Set<CellCoordinate> consumedSources
    ) {
        List<CellCoordinate> route = new ArrayList<>();
        java.util.Set<CellCoordinate> seenInRoute = new java.util.HashSet<>();
        CellCoordinate current = start;

        while (current != null && seenInRoute.add(current)) {
            route.add(current);
            CellCoordinate next = successors.get(current);
            if (next == null) {
                break;
            }
            consumedSources.add(current);
            current = next;
        }

        if (current != null && route.size() > 1 && route.get(0).equals(current)) {
            route.add(current);
        }
        return route;
    }

    private record PendingConnection(CellCoordinate source, CellCoordinate target) {
    }

    private record CustomCellContent(
            String typeSuffix,
            List<String> predicateSegments,
            String graphTail,
            String trailingGraphComment
    ) {
    }
}
