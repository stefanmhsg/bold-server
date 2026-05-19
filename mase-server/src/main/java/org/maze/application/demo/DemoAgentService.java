package org.maze.application.demo;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.api.dto.DemoAgentStepResponseDto;
import org.maze.api.dto.DemoHttpTranscriptDto;
import org.maze.domain.vocab.MazeVocab;

/**
 * Presenter-controlled demo agent that executes one real HTTP request per step.
 */
public class DemoAgentService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_AGENT_NAME = "demo-agent";
    private static final int MAX_STEPS = 2_000;

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String KEY_VALUE = MazeVocab.DYNMAZE_NS + "keyValue";
    private static final String NEEDS_ACTION = MazeVocab.DYNMAZE_NS + "needsAction";
    private static final String FOUND_AT = MazeVocab.DYNMAZE_NS + "foundAt";
    private static final String STATE = MazeVocab.DYNMAZE_NS + "state";
    private static final String LOCKED = MazeVocab.DYNMAZE_NS + "locked";
    private static final String HTTP_REQUEST_URI = "http://www.w3.org/2011/http#requestURI";
    private static final String HYDRA_OPERATION = "http://www.w3.org/ns/hydra/core#operation";
    private static final String HYDRA_TARGET = "http://www.w3.org/ns/hydra/core#target";
    private static final String DYN_ACCEPTS_KEY_TYPE = MazeVocab.DYNMAZE_NS + "acceptsKeyType";
    private static final List<Direction> DIRECTION_ORDER = List.of(
            Direction.WEST,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUri;
    private final String mazeUri;

    private String agentName = DEFAULT_AGENT_NAME;
    private boolean preferGreenSignifiers = true;
    private int step = 0;
    private String currentCell;
    private Phase phase = Phase.DISCOVER_START;
    private String decision = "Ready to discover the maze start.";
    private String error;
    private DemoHttpTranscriptDto lastTranscript;
    private final Deque<String> path = new ArrayDeque<>();
    private final Set<String> visited = new HashSet<>();
    private final Map<String, String> keyringByType = new HashMap<>();
    private PendingMove pendingMove;
    private PendingUnlock pendingUnlock;
    private String discoveredStartCell;

    public DemoAgentService(URI rdfBaseUri) {
        this.baseUri = normalizeBaseUri(rdfBaseUri);
        this.mazeUri = this.baseUri + "/maze";
    }

    public synchronized DemoAgentStepResponseDto reset(String requestedAgentName, boolean requestedPreferGreenSignifiers) {
        this.agentName = sanitizeAgentName(requestedAgentName);
        this.preferGreenSignifiers = requestedPreferGreenSignifiers;
        this.step = 0;
        this.currentCell = null;
        this.phase = Phase.DISCOVER_START;
        this.decision = "Ready to discover the maze start.";
        this.error = null;
        this.lastTranscript = null;
        this.path.clear();
        this.visited.clear();
        this.keyringByType.clear();
        this.pendingMove = null;
        this.pendingUnlock = null;
        this.discoveredStartCell = null;
        return toResponse();
    }

    public synchronized DemoAgentStepResponseDto state() {
        return toResponse();
    }

    public synchronized void setPreferGreenSignifiers(boolean requestedPreferGreenSignifiers) {
        this.preferGreenSignifiers = requestedPreferGreenSignifiers;
    }

    public synchronized DemoAgentStepResponseDto next() {
        if (phase == Phase.COMPLETE || phase == Phase.ERROR) {
            return toResponse();
        }

        if (step >= MAX_STEPS) {
            return fail("Maximum step count reached without completing the demo run.");
        }

        step++;
        try {
            return switch (phase) {
                case DISCOVER_START -> discoverStart();
                case ENTER_START -> enterStart();
                case READ_CELL -> readCurrentCell();
                case UNLOCK -> unlockCurrentCell();
                case MOVE -> moveToPendingTarget();
                case COMPLETE, ERROR -> toResponse();
            };
        } catch (Exception ex) {
            return fail(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }
    }

    private DemoAgentStepResponseDto discoverStart() throws Exception {
        Map<String, String> headers = defaultGetHeaders();
        DemoHttpTranscriptDto transcript = send("GET", mazeUri, headers, null);
        lastTranscript = transcript;

        if (!isSuccess(transcript.responseStatus())) {
            return fail("GET /maze failed with status " + transcript.responseStatus() + ".");
        }

        Model model = parseTurtle(transcript.responseBody(), mazeUri);
        IRI startPredicate = iri(MazeVocab.START);
        discoveredStartCell = model.filter(null, startPredicate, null)
                .stream()
                .findFirst()
                .map(statement -> statement.getObject().stringValue())
                .orElseThrow(() -> new IllegalStateException("xhv:start not found in /maze graph."));
        phase = Phase.ENTER_START;
        decision = "Discovered start cell " + shortCell(discoveredStartCell) + ". Next request enters the maze.";
        return toResponse();
    }

    private DemoAgentStepResponseDto enterStart() throws Exception {
        if (discoveredStartCell == null) {
            throw new IllegalStateException("No discovered start cell. Reset the demo agent.");
        }

        String body = movementBody(mazeUri);
        DemoHttpTranscriptDto transcript = send("POST", discoveredStartCell, defaultPostHeaders(), body);
        lastTranscript = transcript;

        if (!isSuccess(transcript.responseStatus())) {
            return fail("Entering the maze failed with status " + transcript.responseStatus() + ".");
        }

        currentCell = discoveredStartCell;
        path.clear();
        path.push(currentCell);
        visited.add(currentCell);
        phase = Phase.READ_CELL;
        decision = "Entered " + shortCell(currentCell) + ". Next request reads the current cell.";
        return toResponse();
    }

    private DemoAgentStepResponseDto readCurrentCell() throws Exception {
        if (currentCell == null) {
            throw new IllegalStateException("No current cell. Reset the demo agent.");
        }

        DemoHttpTranscriptDto transcript = send("GET", currentCell, defaultGetHeaders(), null);
        lastTranscript = transcript;

        if (!isSuccess(transcript.responseStatus())) {
            return fail("GET current cell failed with status " + transcript.responseStatus() + ".");
        }

        ParsedCell parsed = ParsedCell.from(currentCell, parseTurtle(transcript.responseBody(), currentCell));
        updateKeyring(parsed);
        decideNextAction(parsed);
        return toResponse();
    }

    private DemoAgentStepResponseDto unlockCurrentCell() throws Exception {
        if (pendingUnlock == null) {
            throw new IllegalStateException("No pending unlock action.");
        }

        String body = "<" + pendingUnlock.lockTargetCell() + "> <" + KEY_VALUE + "> \""
                + escapeTurtleLiteral(pendingUnlock.keyValue()) + "\" .\n";
        DemoHttpTranscriptDto transcript = send("POST", pendingUnlock.lockTargetCell(), defaultPostHeaders(), body);
        lastTranscript = transcript;

        if (!isSuccess(transcript.responseStatus())) {
            return fail("Unlock POST failed with status " + transcript.responseStatus() + ".");
        }

        decision = "Posted key value to " + shortCell(pendingUnlock.lockTargetCell())
                + ". Next request re-reads " + shortCell(currentCell) + ".";
        pendingUnlock = null;
        phase = Phase.READ_CELL;
        return toResponse();
    }

    private DemoAgentStepResponseDto moveToPendingTarget() throws Exception {
        if (pendingMove == null) {
            throw new IllegalStateException("No pending movement action.");
        }

        String body = movementBody(pendingMove.fromCell());
        DemoHttpTranscriptDto transcript = send("POST", pendingMove.toCell(), defaultPostHeaders(), body);
        lastTranscript = transcript;

        if (!isSuccess(transcript.responseStatus())) {
            return fail("Move POST failed with status " + transcript.responseStatus() + ".");
        }

        if (pendingMove.kind() == MoveKind.BACKTRACK) {
            if (!path.isEmpty()) {
                path.pop();
            }
            currentCell = pendingMove.toCell();
            decision = "Backtracked to " + shortCell(currentCell) + ". Next request reads the cell.";
        } else {
            currentCell = pendingMove.toCell();
            if (pendingMove.kind() != MoveKind.EXIT) {
                path.push(currentCell);
                visited.add(currentCell);
                decision = "Moved to " + shortCell(currentCell) + ". Next request reads the cell.";
            } else {
                phase = Phase.COMPLETE;
                decision = "Reached exit " + shortCell(currentCell) + ".";
                pendingMove = null;
                return toResponse();
            }
        }

        pendingMove = null;
        phase = Phase.READ_CELL;
        return toResponse();
    }

    private void decideNextAction(ParsedCell parsed) {
        pendingMove = null;
        pendingUnlock = null;

        if (parsed.locked()) {
            Optional<String> keyValue = keyValueFor(parsed.requiredKeyType());
            if (keyValue.isEmpty()) {
                phase = Phase.ERROR;
                error = "Current cell is locked and the demo agent has no matching key.";
                decision = error;
                return;
            }

            pendingUnlock = new PendingUnlock(parsed.lockTargetCell(), keyValue.get());
            phase = Phase.UNLOCK;
            decision = "Cell is locked. Next request posts a matching key value to "
                    + shortCell(parsed.lockTargetCell()) + ".";
            return;
        }

        if (parsed.exit() != null) {
            pendingMove = new PendingMove(currentCell, parsed.exit(), MoveKind.EXIT);
            phase = Phase.MOVE;
            decision = "Exit link is visible. Next request moves to " + shortCell(parsed.exit()) + ".";
            return;
        }

        if (preferGreenSignifiers && parsed.greenTarget() != null
                && parsed.isTraversableTarget(parsed.greenTarget())) {
            pendingMove = new PendingMove(currentCell, parsed.greenTarget(), MoveKind.GREEN);
            phase = Phase.MOVE;
            decision = "Following maze:green signifier to " + shortCell(parsed.greenTarget()) + ".";
            return;
        }

        for (Direction direction : DIRECTION_ORDER) {
            String target = parsed.targetFor(direction).orElse(null);
            if (target == null || visited.contains(target)) {
                continue;
            }

            pendingMove = new PendingMove(currentCell, target, MoveKind.DFS);
            phase = Phase.MOVE;
            decision = "No usable green signifier. DFS chooses "
                    + direction.name().toLowerCase() + " to " + shortCell(target) + ".";
            return;
        }

        Optional<String> parent = parentCell();
        if (parent.isEmpty()) {
            phase = Phase.ERROR;
            error = "DFS exhausted: no unvisited neighbor and no parent to backtrack to.";
            decision = error;
            return;
        }

        pendingMove = new PendingMove(currentCell, parent.get(), MoveKind.BACKTRACK);
        phase = Phase.MOVE;
        decision = "No unvisited neighbors. Next request backtracks to " + shortCell(parent.get()) + ".";
    }

    private Optional<String> parentCell() {
        if (path.size() < 2) {
            return Optional.empty();
        }

        Iterator<String> iterator = path.iterator();
        iterator.next();
        return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
    }

    private Optional<String> keyValueFor(String requiredKeyType) {
        if (requiredKeyType == null || requiredKeyType.isBlank()) {
            return Optional.empty();
        }

        String keyValue = keyringByType.get(requiredKeyType);
        if (keyValue != null) {
            return Optional.of(keyValue);
        }

        keyValue = keyringByType.get(localNameOf(requiredKeyType));
        return Optional.ofNullable(keyValue);
    }

    private void updateKeyring(ParsedCell parsedCell) {
        for (Map.Entry<String, String> entry : parsedCell.keyTypeToValue().entrySet()) {
            keyringByType.put(entry.getKey(), entry.getValue());
            keyringByType.put(localNameOf(entry.getKey()), entry.getValue());
        }
    }

    private DemoHttpTranscriptDto send(String method, String url, Map<String, String> headers, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT);
        headers.forEach(builder::header);

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
        } else if ("GET".equals(method)) {
            builder.GET();
        } else {
            throw new IllegalArgumentException("Unsupported demo method: " + method);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new DemoHttpTranscriptDto(
                method,
                url,
                headers,
                body,
                response.statusCode(),
                response.headers().map(),
                response.body());
    }

    private Map<String, String> defaultGetHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", agentName);
        headers.put("Accept", "text/turtle");
        return headers;
    }

    private Map<String, String> defaultPostHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", agentName);
        headers.put("Content-Type", "text/turtle");
        headers.put("Accept", "text/turtle, text/plain;q=0.1");
        return headers;
    }

    private String movementBody(String fromCell) {
        return "<" + buildAgentIri() + "> <" + MazeVocab.ENTERS_FROM + "> <" + fromCell + "> .\n";
    }

    private String buildAgentIri() {
        return baseUri + "/agents/" + agentName;
    }

    private DemoAgentStepResponseDto fail(String message) {
        phase = Phase.ERROR;
        error = message;
        decision = message;
        return toResponse();
    }

    private DemoAgentStepResponseDto toResponse() {
        return new DemoAgentStepResponseDto(
                step,
                phase == Phase.ERROR ? "error" : phase == Phase.COMPLETE ? "complete" : "ready",
                agentName,
                currentCell,
                phase.name(),
                decision,
                phase == Phase.COMPLETE,
                lastTranscript,
                error);
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static Model parseTurtle(String turtle, String baseUri) throws Exception {
        return Rio.parse(new StringReader(turtle != null ? turtle : ""), baseUri, RDFFormat.TURTLE);
    }

    private static IRI iri(String value) {
        return SimpleValueFactory.getInstance().createIRI(value);
    }

    private static String normalizeBaseUri(URI rdfBaseUri) {
        String value = rdfBaseUri.toString();
        if (value.endsWith("/gsp/")) {
            value = value.substring(0, value.length() - "/gsp/".length());
        } else if (value.endsWith("/gsp")) {
            value = value.substring(0, value.length() - "/gsp".length());
        }

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String sanitizeAgentName(String requestedAgentName) {
        if (requestedAgentName == null || requestedAgentName.isBlank()) {
            return DEFAULT_AGENT_NAME;
        }
        String sanitized = requestedAgentName.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.isBlank() ? DEFAULT_AGENT_NAME : sanitized;
    }

    private static String localNameOf(String iri) {
        if (iri == null) {
            return "";
        }
        int hash = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int idx = Math.max(hash, slash);
        if (idx < 0 || idx + 1 >= iri.length()) {
            return iri;
        }
        return iri.substring(idx + 1);
    }

    private static String shortCell(String cellUri) {
        if (cellUri == null) {
            return "-";
        }
        int cells = cellUri.indexOf("/cells/");
        if (cells >= 0) {
            return cellUri.substring(cells + "/cells/".length());
        }
        return cellUri;
    }

    private static String escapeTurtleLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum Phase {
        DISCOVER_START,
        ENTER_START,
        READ_CELL,
        UNLOCK,
        MOVE,
        COMPLETE,
        ERROR
    }

    private enum MoveKind {
        GREEN,
        DFS,
        BACKTRACK,
        EXIT
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

    private record PendingMove(String fromCell, String toCell, MoveKind kind) {
    }

    private record PendingUnlock(String lockTargetCell, String keyValue) {
    }

    private record ParsedCell(
            String cellUri,
            Map<Direction, String> directions,
            String greenTarget,
            String exit,
            boolean locked,
            String lockTargetCell,
            String requiredKeyType,
            Map<String, String> keyTypeToValue) {

        static ParsedCell from(String cellUri, Model model) {
            IRI subject = iri(cellUri);
            Map<Direction, String> directions = new EnumMap<>(Direction.class);

            for (Direction direction : Direction.values()) {
                model.filter(subject, iri(direction.predicate), null).stream().findFirst().ifPresent(statement -> {
                    String object = statement.getObject().stringValue();
                    if (!object.endsWith("Wall")) {
                        directions.put(direction, object);
                    }
                });
            }

            String greenTarget = model.filter(subject, iri(MazeVocab.MAZE_NS + "green"), null)
                    .stream()
                    .findFirst()
                    .map(statement -> statement.getObject().stringValue())
                    .orElse(null);

            String exit = model.filter(subject, iri(MazeVocab.MAZE_NS + "exit"), null)
                    .stream()
                    .findFirst()
                    .map(statement -> statement.getObject().stringValue())
                    .orElse(null);

            boolean locked = model.contains(subject, iri(STATE), iri(LOCKED));
            LockRequirement lockRequirement = parseLockRequirement(cellUri, subject, model);

            Map<String, String> keyTypeToValue = new HashMap<>();
            for (Statement statement : model.filter(null, iri(KEY_VALUE), null)) {
                Value keyNode = statement.getSubject();
                String keyValue = statement.getObject().stringValue();
                if (keyNode instanceof Resource keyResource) {
                    model.filter(keyResource, iri(RDF_TYPE), null).forEach(typeStatement -> {
                        keyTypeToValue.put(typeStatement.getObject().stringValue(), keyValue);
                    });
                }
            }

            return new ParsedCell(
                    cellUri,
                    directions,
                    greenTarget,
                    exit,
                    locked,
                    lockRequirement.lockTargetCell(),
                    lockRequirement.requiredKeyType(),
                    keyTypeToValue);
        }

        private static LockRequirement parseLockRequirement(String cellUri, IRI subject, Model model) {
            Optional<Value> hydraAction = model.filter(subject, iri(HYDRA_OPERATION), null)
                    .stream()
                    .findFirst()
                    .map(Statement::getObject);
            if (hydraAction.isPresent() && hydraAction.get() instanceof Resource actionResource) {
                String lockTarget = model.filter(actionResource, iri(HYDRA_TARGET), null)
                        .stream()
                        .findFirst()
                        .map(statement -> statement.getObject().stringValue())
                        .orElse(cellUri);
                String keyType = model.filter(actionResource, iri(DYN_ACCEPTS_KEY_TYPE), null)
                        .stream()
                        .findFirst()
                        .map(statement -> statement.getObject().stringValue())
                        .orElse(null);
                return new LockRequirement(lockTarget, keyType);
            }

            Optional<Value> needsAction = model.filter(subject, iri(NEEDS_ACTION), null)
                    .stream()
                    .findFirst()
                    .map(Statement::getObject);
            String lockTarget = cellUri;
            if (needsAction.isPresent() && needsAction.get() instanceof Resource actionResource) {
                lockTarget = model.filter(actionResource, iri(HTTP_REQUEST_URI), null)
                        .stream()
                        .findFirst()
                        .map(statement -> statement.getObject().stringValue())
                        .orElse(cellUri);
            }
            String keyType = model.filter(null, iri(FOUND_AT), null)
                    .stream()
                    .findFirst()
                    .map(statement -> statement.getObject().stringValue())
                    .orElse(null);
            return new LockRequirement(lockTarget, keyType);
        }

        Optional<String> targetFor(Direction direction) {
            return Optional.ofNullable(directions.get(direction));
        }

        boolean isTraversableTarget(String target) {
            return directions.containsValue(target);
        }
    }

    private record LockRequirement(String lockTargetCell, String requiredKeyType) {
    }
}
