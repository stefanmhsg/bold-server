package org.maze.scenarios.masecreator.agents;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.domain.vocab.MazeVocab;

public class SampleDfsAgentBob {

    private static final String BASE_URI = "http://127.0.1.1:8080";
    private static final String AGENT_NAME = "bob";
    private static final String MAZE_URI = BASE_URI + "/maze";
    private static final int MAX_STEPS = 2_000;

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String KEY_VALUE = MazeVocab.DYNMAZE_NS + "keyValue";
    private static final String NEEDS_ACTION = MazeVocab.DYNMAZE_NS + "needsAction";
    private static final String FOUND_AT = MazeVocab.DYNMAZE_NS + "foundAt";
    private static final String STATE = MazeVocab.DYNMAZE_NS + "state";
    private static final String LOCKED = MazeVocab.DYNMAZE_NS + "locked";
    private static final String HTTP_REQUEST_URI = "http://www.w3.org/2011/http#requestURI";
        private static final List<Direction> DIRECTION_ORDER = List.of(
            Direction.WEST,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Map<String, String> keyringByType = new HashMap<>();

    public static void main(String[] args) throws Exception {
        new SampleDfsAgentBob().run();
    }

    public void run() throws Exception {
        String currentCell = fetchStartCellFromMaze();
        System.out.println("Start cell discovered: " + currentCell);

        int startMoveStatus = postMove(MAZE_URI, currentCell);
        if (startMoveStatus < 200 || startMoveStatus >= 300) {
            throw new IllegalStateException("Failed to enter maze at start cell. status=" + startMoveStatus + " start=" + currentCell);
        }
        System.out.println("Entered maze at: " + currentCell);

        Deque<String> path = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        path.push(currentCell);
        visited.add(currentCell);

        int step = 0;
        while (step < MAX_STEPS) {
            step++;
            ParsedCell parsed = getCell(currentCell);
            updateKeyring(parsed);

            int depth = path.size() - 1;
            logStep(step, depth, "VISIT", currentCell + " | neighbors=" + parsed.neighborSummary());

            if (parsed.locked()) {
                logStep(step, depth, "LOCKED", "attempt unlock at " + currentCell);
                boolean unlocked = tryUnlockCell(parsed);
                if (!unlocked) {
                    throw new IllegalStateException("Current cell is locked and no matching key is available: " + currentCell);
                }
                parsed = getCell(currentCell);
                updateKeyring(parsed);
                logStep(step, depth, "UNLOCKED", currentCell);
            }

            if (parsed.exit() != null) {
                String exitCell = parsed.exit();
                int status = postMove(currentCell, exitCell);
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("Move to exit failed. status=" + status + " from=" + currentCell + " to=" + exitCell);
                }
                currentCell = exitCell;
                logStep(step, depth, "EXIT", "moved to " + currentCell);
                if (currentCell.endsWith("/cells/999")) {
                    System.out.println("SUCCESS: bob reached maze exit cell /cells/999 in " + step + " steps.");
                    return;
                }
                continue;
            }

            boolean advanced = false;
            for (Direction direction : DIRECTION_ORDER) {
                String nextCell = parsed.targetFor(direction).orElse(null);
                if (nextCell == null) {
                    continue;
                }

                if (visited.contains(nextCell)) {
                    logStep(step, depth, "SKIP", direction.name().toLowerCase() + " -> already visited " + nextCell);
                    continue;
                }

                int moveStatus = postMove(currentCell, nextCell);
                if (moveStatus >= 200 && moveStatus < 300) {
                    String fromCell = currentCell;
                    currentCell = nextCell;
                    path.push(currentCell);
                    visited.add(currentCell);
                    logStep(step, depth, "MOVE", direction.name().toLowerCase() + " : " + fromCell + " -> " + currentCell);
                    advanced = true;
                    break;
                }

                if (moveStatus == 403 || moveStatus == 409) {
                    logStep(step, depth, "BLOCKED", direction.name().toLowerCase() + " -> " + nextCell + " (status=" + moveStatus + ")");
                    continue;
                }

                throw new IllegalStateException("Move failed with unexpected status=" + moveStatus + " from=" + currentCell + " to=" + nextCell);
            }

            if (advanced) {
                continue;
            }

            if (path.size() <= 1) {
                throw new IllegalStateException("DFS exhausted: no unvisited neighbors and no parent to backtrack to from " + currentCell);
            }

            String deadEnd = path.pop();
            String parent = path.peek();
            int backtrackStatus = postMove(deadEnd, parent);
            if (backtrackStatus < 200 || backtrackStatus >= 300) {
                throw new IllegalStateException("Backtrack failed. status=" + backtrackStatus + " from=" + deadEnd + " to=" + parent);
            }
            currentCell = parent;
            logStep(step, depth, "BACKTRACK", deadEnd + " -> " + parent);
        }

        throw new IllegalStateException("Max steps reached without finding exit: " + MAX_STEPS);
    }

    private ParsedCell getCell(String cellUri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(cellUri))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .header("Authorization", AGENT_NAME)
                .header("Accept", "text/turtle")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GET failed for " + cellUri + " status=" + response.statusCode() + " body=" + response.body());
        }

        Model model = Rio.parse(new StringReader(response.body()), cellUri, RDFFormat.TURTLE);
        return ParsedCell.from(cellUri, model);
    }

    private String fetchStartCellFromMaze() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MAZE_URI))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .header("Authorization", AGENT_NAME)
                .header("Accept", "text/turtle")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GET /maze failed: status=" + response.statusCode() + " body=" + response.body());
        }

        Model model = Rio.parse(new StringReader(response.body()), MAZE_URI, RDFFormat.TURTLE);
        IRI startPredicate = iri(MazeVocab.START);

        return model.filter(null, startPredicate, null)
                .stream()
                .findFirst()
                .map(statement -> statement.getObject().stringValue())
                .orElseThrow(() -> new IllegalStateException("xhv:start not found in /maze graph"));
    }

    private int postMove(String fromCell, String toCell) throws Exception {
        String agentIri = buildAgentIri(fromCell);
        String turtle = "<" + agentIri + "> <" + MazeVocab.ENTERS_FROM + "> <" + fromCell + "> .\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create(toCell))
                .timeout(Duration.ofSeconds(10))
            .header("Authorization", AGENT_NAME)
                .header("Content-Type", "text/turtle")
                .POST(HttpRequest.BodyPublishers.ofString(turtle))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private boolean tryUnlockCell(ParsedCell parsedCell) throws Exception {
        if (!parsedCell.locked() || parsedCell.lockTargetCell() == null || parsedCell.requiredKeyType() == null) {
            return !parsedCell.locked();
        }

        String needed = parsedCell.requiredKeyType();
        String keyValue = keyringByType.get(needed);
        if (keyValue == null) {
            String localName = localNameOf(needed);
            keyValue = keyringByType.get(localName);
        }
        if (keyValue == null) {
            return false;
        }

        String turtle = "<" + parsedCell.lockTargetCell() + "> <" + KEY_VALUE + "> \"" + keyValue + "\" .\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create(parsedCell.lockTargetCell()))
                .timeout(Duration.ofSeconds(10))
            .header("Authorization", AGENT_NAME)
                .header("Content-Type", "text/turtle")
                .POST(HttpRequest.BodyPublishers.ofString(turtle))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }

        ParsedCell refreshed = getCell(parsedCell.cellUri());
        updateKeyring(refreshed);
        return !refreshed.locked();
    }

    private void updateKeyring(ParsedCell parsedCell) {
        for (Map.Entry<String, String> entry : parsedCell.keyTypeToValue().entrySet()) {
            keyringByType.put(entry.getKey(), entry.getValue());
            keyringByType.put(localNameOf(entry.getKey()), entry.getValue());
        }
    }

    private static String localNameOf(String iri) {
        int hash = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int idx = Math.max(hash, slash);
        if (idx < 0 || idx + 1 >= iri.length()) {
            return iri;
        }
        return iri.substring(idx + 1);
    }

    private static IRI iri(String value) {
        return SimpleValueFactory.getInstance().createIRI(value);
    }

    private static String buildAgentIri(String cellOrMazeUri) {
        int cellsIndex = cellOrMazeUri.lastIndexOf("/cells");
        if (cellsIndex > 0) {
            return cellOrMazeUri.substring(0, cellsIndex) + "/agents/" + AGENT_NAME;
        }
        int mazeIndex = cellOrMazeUri.lastIndexOf("/maze");
        if (mazeIndex > 0) {
            return cellOrMazeUri.substring(0, mazeIndex) + "/agents/" + AGENT_NAME;
        }
        return BASE_URI + "/agents/" + AGENT_NAME;
    }

    private void logStep(int step, int depth, String action, String details) {
        String indent = "  ".repeat(Math.max(depth, 0));
        System.out.println(String.format("[step=%04d depth=%02d] %s%-9s %s", step, depth, indent, action, details));
    }

    private enum Direction {
        WEST(MazeVocab.MAZE_NS + "west"),
        NORTH(MazeVocab.MAZE_NS + "north"),
        EAST(MazeVocab.MAZE_NS + "east"),
        SOUTH(MazeVocab.MAZE_NS + "south");

        private final String predicate;

        Direction(String predicate) {
            this.predicate = predicate;
        }

    }

    private record ParsedCell(
            String cellUri,
            Map<Direction, String> directions,
            String exit,
            boolean locked,
            String lockTargetCell,
            String requiredKeyType,
            Map<String, String> keyTypeToValue) {

        static ParsedCell from(String cellUri, Model model) {
            Map<Direction, String> directions = new EnumMap<>(Direction.class);
            IRI subject = iri(cellUri);

            for (Direction direction : Direction.values()) {
                IRI predicate = iri(direction.predicate);
                model.filter(subject, predicate, null).stream().findFirst().ifPresent(statement -> {
                    String obj = statement.getObject().stringValue();
                    if (!obj.endsWith("Wall")) {
                        directions.put(direction, obj);
                    }
                });
            }

            String exit = model.filter(subject, iri(MazeVocab.MAZE_NS + "exit"), null)
                    .stream()
                    .findFirst()
                    .map(statement -> statement.getObject().stringValue())
                    .orElse(null);

            boolean locked = model.contains(subject, iri(STATE), iri(LOCKED));

                String lockTarget = cellUri;
                Optional<Value> actionNode = model.filter(subject, iri(NEEDS_ACTION), null)
                    .stream()
                    .findFirst()
                    .map(Statement::getObject);
                if (actionNode.isPresent() && actionNode.get() instanceof Resource actionResource) {
                lockTarget = model.filter(actionResource, iri(HTTP_REQUEST_URI), null)
                    .stream()
                    .findFirst()
                    .map(requestStmt -> requestStmt.getObject().stringValue())
                    .orElse(cellUri);
                }

            String requiredKeyType = model.filter(null, iri(FOUND_AT), null)
                    .stream()
                    .findFirst()
                    .map(statement -> statement.getObject().stringValue())
                    .orElse(null);

            Map<String, String> keyTypeToValue = new HashMap<>();
            for (Statement statement : model.filter(null, iri(KEY_VALUE), null)) {
                Value keyNode = statement.getSubject();
                String keyValue = statement.getObject().stringValue();
                if (keyNode instanceof Resource keyResource) {
                    model.filter(keyResource, iri(RDF_TYPE), null).forEach(typeStmt -> {
                        keyTypeToValue.put(typeStmt.getObject().stringValue(), keyValue);
                    });
                }
            }

            return new ParsedCell(cellUri, directions, exit, locked, lockTarget, requiredKeyType, keyTypeToValue);
        }

        Optional<String> targetFor(Direction direction) {
            return Optional.ofNullable(directions.get(direction));
        }

        String neighborSummary() {
            List<String> summary = new ArrayList<>();
            for (Direction direction : DIRECTION_ORDER) {
                summary.add(direction.name().toLowerCase() + "=" + targetFor(direction).orElse("wall"));
            }
            return summary.stream().collect(Collectors.joining(", "));
        }
    }
}
