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

    public MazeModel parse(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    public MazeModel parse(String trig) {
        MazeModel model = MazeModel.blank(1, 1);
        List<PendingConnection> pendingConnections = new ArrayList<>();
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
        }

        for (PendingConnection connection : pendingConnections) {
            model.connectIfAdjacent(connection.source(), connection.target());
        }

        parseStart(trig).ifPresent(model::setStartFromParser);
        if (parsedExitSource != null) {
            model.setExitFromParser(parsedExitSource);
        }

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

    private record PendingConnection(CellCoordinate source, CellCoordinate target) {
    }
}
