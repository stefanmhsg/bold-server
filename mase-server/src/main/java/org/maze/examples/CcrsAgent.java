package org.maze.examples;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

public class CcrsAgent {

    private static final String BASE_URI = "http://127.0.1.1:8080";
    private static final String MAZE_URI = BASE_URI + "/maze";
    private static final int MAX_STEPS = 2_000;
    private static final long AGENT_DISPATCH_INTERVAL_MS = envLong("CCRS_AGENT_DISPATCH_INTERVAL_MS", 3_000);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(envLong("CCRS_AGENT_REQUEST_TIMEOUT_SECONDS", 10));

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String KEY_VALUE = MazeVocab.DYNMAZE_NS + "keyValue";
    private static final String HYDRA_OPERATION = "http://www.w3.org/ns/hydra/core#operation";
    private static final String DYN_ACCEPTS_KEY_TYPE = MazeVocab.DYNMAZE_NS + "acceptsKeyType";
    private static final String HYDRA_TARGET = "http://www.w3.org/ns/hydra/core#target";
    private static final String STATE = MazeVocab.DYNMAZE_NS + "state";
    private static final String LOCKED = MazeVocab.DYNMAZE_NS + "locked";
    private static final String CELLS_SEGMENT = "/cells/";

    // When a configured agent reaches 37/31, it will try 36/31 then 35/31.
    private static final List<String> GREEN_KEY_LOCATION_COORDINATES = List.of("7/2", "7/3", "7/4", "8/4", "8/5", "8/6", "9/6", "10/6", "11/6", "11/5");
    private static final List<String> MIXED_ZONE_EMERGENCY_COORDINATES = List.of("37/31", "36/31", "35/31", "35/30", "35/29", "35/28", "35/27", "34/27", "33/27", "33/26", "33/25", "33/24", "32/24", "31/24", "31/25");
    private static final List<String> CONSTRUCTION_SITE_ZONE_COORDINATES = List.of("36/39", "35/39", "34/39", "34/40", "33/40", "32/40", "31/40");
    private static final List<String> PASS_RED_LOCK_DIRECTLY = List.of("36/36", "36/37");
    private static final List<String> GOTO_EXIT_DIRECTLY = List.of("48/49", "48/50");



    // Configure each spawned agent and its exploration direction preference here.

    // DFS Agent
    /*
    private static final List<AgentConfig> AGENT_CONFIGS = List.of(
            new AgentConfig("ccrs-agent-1", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), CONSTRUCTION_SITE_ZONE_COORDINATES)
        );
    */
    // Evaluation Config
    
    private static final List<AgentConfig> AGENT_CONFIGS = List.of(
            new AgentConfig("ccrs-agent-1.1", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.2", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.3", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.4", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.5", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.6", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.7", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.8", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-1.9", List.of(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), List.of(GREEN_KEY_LOCATION_COORDINATES, MIXED_ZONE_EMERGENCY_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, PASS_RED_LOCK_DIRECTLY, GOTO_EXIT_DIRECTLY)),
        
            new AgentConfig("ccrs-agent-2.1", List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST), List.of(GREEN_KEY_LOCATION_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, GOTO_EXIT_DIRECTLY)),
            new AgentConfig("ccrs-agent-2.2", List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST), List.of(GREEN_KEY_LOCATION_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, GOTO_EXIT_DIRECTLY)),
            
            new AgentConfig("ccrs-agent-3", List.of(Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH), List.of(GREEN_KEY_LOCATION_COORDINATES, CONSTRUCTION_SITE_ZONE_COORDINATES, GOTO_EXIT_DIRECTLY))
        );
    

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Map<String, String> keyringByType = new HashMap<>();
    private final String agentName;
    private final List<Direction> directionOrder;
    private final List<List<String>> guidedCoordinateLists;

    private CcrsAgent(AgentConfig config) {
        this.agentName = config.name();
        this.directionOrder = List.copyOf(config.directionOrder());
        this.guidedCoordinateLists = config.guidedCoordinateLists().stream()
            .map(List::copyOf)
            .toList();
    }

    public static void main(String[] args) throws Exception {
        runConfiguredAgents();
    }

    private static void runConfiguredAgents() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(AGENT_CONFIGS.size());
        try {
            System.out.println("CCRS agent dispatch interval: " + AGENT_DISPATCH_INTERVAL_MS + " ms");
            System.out.println("CCRS HTTP request timeout: " + REQUEST_TIMEOUT.toSeconds() + " s");
            List<Future<String>> results = new ArrayList<>();
            for (AgentConfig config : AGENT_CONFIGS) {
                Future<String> future = executor.submit(() -> {
                        CcrsAgent agent = new CcrsAgent(config);
                        agent.run();
                        return config.name();
                });
                results.add(future);
                Thread.sleep(AGENT_DISPATCH_INTERVAL_MS);
            }

            List<String> failures = new ArrayList<>();
            for (Future<String> result : results) {
                try {
                    String completedAgent = result.get();
                    System.out.println("Agent finished: " + completedAgent);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    failures.add(cause.getMessage() != null ? cause.getMessage() : cause.toString());
                }
            }

            if (!failures.isEmpty()) {
                throw new IllegalStateException("One or more agents failed: " + failures);
            }
        } finally {
            executor.shutdownNow();
        }
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
            logStep(step, depth, "VISIT", currentCell + " | neighbors=" + parsed.neighborSummary(directionOrder));

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
                    System.out.println("SUCCESS: ccrs-agent reached maze exit cell /cells/999 in " + step + " steps.");
                    return;
                }
                continue;
            }

            boolean advanced = false;

            Optional<String> guidedNextCell = guidedNextCell(currentCell);
            if (guidedNextCell.isPresent()) {
                String nextCell = guidedNextCell.get();
                int moveStatus = postMove(currentCell, nextCell);
                if (moveStatus >= 200 && moveStatus < 300) {
                    String fromCell = currentCell;
                    currentCell = nextCell;
                    path.push(currentCell);
                    visited.add(currentCell);
                    logStep(step, depth, "GUIDED", fromCell + " -> " + currentCell);
                    advanced = true;
                } else {
                    throw new IllegalStateException("Guided move failed. status=" + moveStatus + " from=" + currentCell + " to=" + nextCell);
                }
            }

            if (advanced) {
                continue;
            }

            for (Direction direction : directionOrder) {
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
                .timeout(REQUEST_TIMEOUT)
                .GET()
            .header("Authorization", agentName)
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
                .timeout(REQUEST_TIMEOUT)
                .GET()
            .header("Authorization", agentName)
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
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", agentName)
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
            String currentCoordinate = coordinateOf(parsedCell.cellUri());
            if ("36/36".equals(currentCoordinate)) {
                keyValue = "redkey-1670";
            } else if ("44/45".equals(currentCoordinate)) {
                keyValue = "bluekey-9347";
            }
        }
        if (keyValue == null) {
            return false;
        }

        String turtle = "<" + parsedCell.lockTargetCell() + "> <" + KEY_VALUE + "> \"" + keyValue + "\" .\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create(parsedCell.lockTargetCell()))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", agentName)
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

    private static long envLong(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            System.out.println("Ignoring invalid " + name + "=" + value + "; using " + defaultValue);
            return defaultValue;
        }
    }

    private String buildAgentIri(String cellOrMazeUri) {
        int cellsIndex = cellOrMazeUri.lastIndexOf("/cells");
        if (cellsIndex > 0) {
            return cellOrMazeUri.substring(0, cellsIndex) + "/agents/" + agentName;
        }
        int mazeIndex = cellOrMazeUri.lastIndexOf("/maze");
        if (mazeIndex > 0) {
            return cellOrMazeUri.substring(0, mazeIndex) + "/agents/" + agentName;
        }
        return BASE_URI + "/agents/" + agentName;
    }

    private Optional<String> guidedNextCell(String currentCellUri) {
        String currentCoordinate = coordinateOf(currentCellUri);
        if (currentCoordinate == null) {
            return Optional.empty();
        }

        for (List<String> guidedCoordinates : guidedCoordinateLists) {
            if (guidedCoordinates.size() < 2) {
                continue;
            }
            for (int i = 0; i < guidedCoordinates.size() - 1; i++) {
                if (guidedCoordinates.get(i).equals(currentCoordinate)) {
                    String nextCoordinate = guidedCoordinates.get(i + 1);
                    return Optional.of(toCellUri(currentCellUri, nextCoordinate));
                }
            }
        }

        return Optional.empty();
    }

    private static String coordinateOf(String cellUri) {
        int cellsIndex = cellUri.indexOf(CELLS_SEGMENT);
        if (cellsIndex < 0) {
            return null;
        }

        String suffix = cellUri.substring(cellsIndex + CELLS_SEGMENT.length());
        int queryIndex = suffix.indexOf('?');
        if (queryIndex >= 0) {
            suffix = suffix.substring(0, queryIndex);
        }
        int fragmentIndex = suffix.indexOf('#');
        if (fragmentIndex >= 0) {
            suffix = suffix.substring(0, fragmentIndex);
        }

        return suffix;
    }

    private static String toCellUri(String currentCellUri, String coordinate) {
        int cellsIndex = currentCellUri.indexOf(CELLS_SEGMENT);
        if (cellsIndex < 0) {
            throw new IllegalStateException("Cannot build guided URI without /cells segment: " + currentCellUri);
        }
        String base = currentCellUri.substring(0, cellsIndex + CELLS_SEGMENT.length());
        String normalizedCoordinate = coordinate.startsWith("/") ? coordinate.substring(1) : coordinate;
        return base + normalizedCoordinate;
    }

    private void logStep(int step, int depth, String action, String details) {
        String indent = "  ".repeat(Math.max(depth, 0));
        System.out.println(String.format("[%s step=%04d depth=%02d] %s%-9s %s", agentName, step, depth, indent, action, details));
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

    private record AgentConfig(String name, List<Direction> directionOrder, List<List<String>> guidedCoordinateLists) {
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
            String requiredKeyType = null;
            Optional<Value> actionNode = model.filter(subject, iri(HYDRA_OPERATION), null)
                    .stream()
                    .findFirst()
                    .map(Statement::getObject);
                if (actionNode.isPresent() && actionNode.get() instanceof Resource actionResource) {
                    lockTarget = model.filter(actionResource, iri(HYDRA_TARGET), null)
                        .stream()
                        .findFirst()
                        .map(requestStmt -> requestStmt.getObject().stringValue())
                        .orElse(cellUri);

                    requiredKeyType = model.filter(actionResource, iri(DYN_ACCEPTS_KEY_TYPE), null)
                        .stream()
                        .findFirst()
                        .map(typeStmt -> typeStmt.getObject().stringValue())
                        .orElse(null);
                }

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

        String neighborSummary(List<Direction> directionOrder) {
            List<String> summary = new ArrayList<>();
            for (Direction direction : directionOrder) {
                summary.add(direction.name().toLowerCase() + "=" + targetFor(direction).orElse("wall"));
            }
            return summary.stream().collect(Collectors.joining(", "));
        }
    }
}
