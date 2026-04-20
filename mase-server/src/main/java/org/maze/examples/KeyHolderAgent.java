package org.maze.examples;

import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.events.EventQueue;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStateProvider;
import io.a2a.server.tasks.TaskStore;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.a2a.spec.TransportProtocol;
import io.a2a.transport.rest.handler.RestHandler;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.domain.vocab.MazeVocab;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class KeyHolderAgent {

    private static final String BASE_URI = "http://127.0.1.1:8080";
    private static final String MAZE_URI = BASE_URI + "/maze";
    private static final String AGENT_NAME = "key-holder-agent-2";
    private static final String TARGET_COORDINATE = "33/35";
    private static final int DEFAULT_A2A_PORT = 8095;
    private static final int MAX_STEPS = 2_000;
    private static final String CELLS_SEGMENT = "/cells/";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String KEY_VALUE = MazeVocab.DYNMAZE_NS + "keyValue";
    private static final String NEEDS_ACTION = MazeVocab.DYNMAZE_NS + "needsAction";
    private static final String FOUND_AT = MazeVocab.DYNMAZE_NS + "foundAt";
    private static final String STATE = MazeVocab.DYNMAZE_NS + "state";
    private static final String LOCKED = MazeVocab.DYNMAZE_NS + "locked";
    private static final String HTTP_REQUEST_URI = "http://www.w3.org/2011/http#requestURI";
        private static final String RED_KEY_TURTLE = """
                        @prefix dyn: <http://example.org/dynamic-maze#> .

                        <http://127.0.1.1:8080/cells/37/36#key> a dyn:RedKey;
                            dyn:fitsInLock <http://127.0.1.1:8080/cells/36/36>;
                            dyn:keyValue "redkey" .
                        """;

    private static final List<Direction> DIRECTION_ORDER = List.of(
            Direction.SOUTH,
            Direction.EAST,
            Direction.NORTH,
            Direction.WEST);

    private static final List<String> MIXED_ZONE_EMERGENCY_COORDINATES = List.of(
            "37/31", "36/31", "35/31", "35/30", "35/29", "35/28", "35/27",
            "34/27", "33/27", "33/26", "33/25", "33/24", "32/24", "31/24", "31/25");

    private static final List<List<String>> GUIDED_COORDINATE_LISTS = List.of(MIXED_ZONE_EMERGENCY_COORDINATES);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Map<String, String> keyringByType = new java.util.HashMap<>();
    private final String agentName = AGENT_NAME;

    private final ExecutorService a2aExecutor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) throws Exception {
        new KeyHolderAgent().start();
    }

    public void start() throws Exception {
        int port = requirePreferredPort(resolvePreferredPort());
        RestHandler restHandler = createRestHandler(port);
        HttpServer server = createA2AHttpServer(port, restHandler);
        server.start();
        logA2A("Server started on http://127.0.0.1:" + port + " as " + agentName);
        logA2A("AgentCard endpoint: /.well-known/agent-card.json");
        logA2A("Message endpoints: /message/send and /message:send");

        try {
            runMazeBehaviour();
        } catch (Exception ex) {
            logA2A("Maze behavior failed, but A2A server stays online: " + ex.getMessage());
            // Keep process alive so A2A requests still work.
            keepAlive();
        }
    }

        private RestHandler createRestHandler(int port) {
        AgentExecutor agentExecutor = new RedKeyAgentExecutor();
        TaskStore taskStore = new InMemoryTaskStore();
        QueueManager queueManager = new InMemoryQueueManager((TaskStateProvider) taskStore);
        PushNotificationConfigStore pushConfigStore = new InMemoryPushNotificationConfigStore();
        PushNotificationSender pushNotificationSender = new BasePushNotificationSender(pushConfigStore);

        RequestHandler requestHandler = DefaultRequestHandler.create(
            agentExecutor,
            taskStore,
            queueManager,
            pushConfigStore,
            pushNotificationSender,
            a2aExecutor);

        return new RestHandler(createAgentCard(port), requestHandler, a2aExecutor);
        }

        private AgentCard createAgentCard(int port) {
        AgentCapabilities capabilities = new AgentCapabilities.Builder()
            .streaming(false)
            .pushNotifications(false)
            .build();

        AgentSkill skill = new AgentSkill.Builder()
            .id("provide_red_key")
            .name("Provide Red Key")
            .description("Returns RDF triple of red key")
            .tags(List.of("key", "red-key", "a2a"))
            .examples(List.of("provide_red_key"))
            .inputModes(List.of("text"))
            .outputModes(List.of("text"))
            .security(List.of())
            .build();

        String a2aBaseUrl = "http://127.0.0.1:" + port;
        String sendUrl = a2aBaseUrl + "/message/send";

        return new AgentCard.Builder()
            .name("Key Holder Agent (" + agentName + ")")
            .description("Provides red key via A2A")
            .url(sendUrl)
            .version("1.0.0")
            .capabilities(capabilities)
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(skill))
            .additionalInterfaces(List.of(
                new AgentInterface(TransportProtocol.HTTP_JSON.asString(), sendUrl)
            ))
            .preferredTransport(TransportProtocol.HTTP_JSON.asString())
            .build();
        }

    private HttpServer createA2AHttpServer(int port, RestHandler restHandler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/.well-known/agent-card.json", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            logA2A("GET /.well-known/agent-card.json from " + exchange.getRemoteAddress());
            RestHandler.HTTPRestResponse response = restHandler.getAgentCard();
            logA2A("AgentCard response status=" + response.getStatusCode());
            respond(exchange, response.getStatusCode(), response.getContentType(), response.getBody());
        });

        server.createContext("/message/send", exchange -> {
            handleMessageSend(exchange, restHandler, "/message/send");
        });

        // Some A2A clients use the colon variant from JSON-RPC routing conventions.
        server.createContext("/message:send", exchange -> {
            handleMessageSend(exchange, restHandler, "/message:send");
        });

        // Convenience endpoint for legacy direct retrieval.
        server.createContext("/provide_red_key", exchange -> {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                respond(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            respond(exchange, 200, "text/turtle", RED_KEY_TURTLE);
        });

        return server;
    }

    private void handleMessageSend(HttpExchange exchange, RestHandler restHandler, String endpoint) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            respond(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        logA2A("POST " + endpoint + " from " + exchange.getRemoteAddress());
        try {
            String requestBody = readBody(exchange);
            logA2A("Request body preview: " + summarizePayload(requestBody));
            RestHandler.HTTPRestResponse response = restHandler.sendMessage(requestBody, buildCallContext());
            logA2A("Response status=" + response.getStatusCode() + " contentType=" + response.getContentType());
            respond(exchange, response.getStatusCode(), response.getContentType(), response.getBody());
        } catch (Exception ex) {
            logA2A("Message handling failed: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            String body = "{\"error\":\"internal_error\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}";
            respond(exchange, 500, "application/json", body);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String summarizePayload(String payload) {
        if (payload == null) {
            return "<null>";
        }
        String normalized = payload.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 220) {
            return normalized;
        }
        return normalized.substring(0, 220) + "...";
    }

    private int resolvePreferredPort() {
        String envValue = System.getenv("MASE_KEYHOLDER_PORT");
        if (envValue == null || envValue.isBlank()) {
            return DEFAULT_A2A_PORT;
        }
        try {
            return Integer.parseInt(envValue.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_A2A_PORT;
        }
    }

    private int requirePreferredPort(int preferredPort) throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", preferredPort));
            return preferredPort;
        } catch (IOException ex) {
            throw new IOException("Preferred A2A port already in use: " + preferredPort, ex);
        }
    }

    private static ServerCallContext buildCallContext() {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private static final class RedKeyAgentExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, EventQueue queue) {
            String taskId = context.getTask() == null ? "<new-task>" : context.getTask().getId();
            System.out.println("[" + AGENT_NAME + " A2A] Executor.execute task=" + taskId + " -> returning red key artifact");
            TaskUpdater updater = new TaskUpdater(context, queue);
            if (context.getTask() == null) {
                updater.submit();
            }
            updater.startWork();
            List<Part<?>> parts = List.of(new TextPart(RED_KEY_TURTLE));
            updater.addArtifact(parts, "red-key", "text/turtle", Map.of("contentType", "text/turtle"));
            updater.complete();
            System.out.println("[" + AGENT_NAME + " A2A] Executor.complete task=" + taskId);
        }

        @Override
        public void cancel(RequestContext context, EventQueue queue) {
            String taskId = context.getTask() == null ? "<unknown-task>" : context.getTask().getId();
            System.out.println("[" + AGENT_NAME + " A2A] Executor.cancel task=" + taskId);
            TaskUpdater updater = new TaskUpdater(context, queue);
            updater.cancel();
        }
    }

    private void logA2A(String message) {
        System.out.println("[" + agentName + " A2A] " + message);
    }

    private void runMazeBehaviour() throws Exception {
        String currentCell = fetchStartCellFromMaze();
        logStep(0, 0, "START", "start cell discovered: " + currentCell);

        int startMoveStatus = postMove(MAZE_URI, currentCell);
        if (startMoveStatus < 200 || startMoveStatus >= 300) {
            throw new IllegalStateException("Failed to enter maze at start cell. status=" + startMoveStatus + " start=" + currentCell);
        }
        logStep(0, 0, "ENTER", "entered maze at: " + currentCell);

        Deque<String> path = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        path.push(currentCell);
        visited.add(currentCell);

        int step = 0;
        while (step < MAX_STEPS) {
            step++;

            ParsedCell parsed = getCell(currentCell);
            updateKeyring(parsed);

            if (TARGET_COORDINATE.equals(coordinateOf(currentCell))) {
                logStep(step, Math.max(path.size() - 1, 0), "HOLD", "target reached at " + currentCell + "; agent stays idle and online");
                idleForever();
                return;
            }

            int depth = path.size() - 1;
            logStep(step, depth, "VISIT", currentCell + " | neighbors=" + parsed.neighborSummary(DIRECTION_ORDER));

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
                    logStep(step, depth, "GUIDEFAIL", "status=" + moveStatus + " from=" + currentCell + " to=" + nextCell);
                }
            }

            if (advanced) {
                continue;
            }

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
                logStep(step, Math.max(path.size() - 1, 0), "HOLD", "target " + TARGET_COORDINATE + " not reachable; staying idle at " + currentCell);
                idleForever();
                return;
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

        logStep(step, Math.max(path.size() - 1, 0), "HOLD", "max steps reached; staying idle at " + currentCell);
        idleForever();
    }

    private void idleForever() throws Exception {
        while (true) {
            Thread.sleep(1_000);
        }
    }

    private void keepAlive() throws Exception {
        while (true) {
            Thread.sleep(1_000);
        }
    }

    private ParsedCell getCell(String cellUri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(cellUri))
                .timeout(Duration.ofSeconds(10))
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
                .timeout(Duration.ofSeconds(10))
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
                .timeout(Duration.ofSeconds(10))
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
            return false;
        }

        String turtle = "<" + parsedCell.lockTargetCell() + "> <" + KEY_VALUE + "> \"" + keyValue + "\" .\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create(parsedCell.lockTargetCell()))
                .timeout(Duration.ofSeconds(10))
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

    private Optional<String> guidedNextCell(String currentCellUri) {
        String currentCoordinate = coordinateOf(currentCellUri);
        if (currentCoordinate == null) {
            return Optional.empty();
        }

        for (List<String> guidedCoordinates : GUIDED_COORDINATE_LISTS) {
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

    private static IRI iri(String value) {
        return SimpleValueFactory.getInstance().createIRI(value);
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
            Optional<org.eclipse.rdf4j.model.Value> actionNode = model.filter(subject, iri(NEEDS_ACTION), null)
                    .stream()
                    .findFirst()
                    .map(org.eclipse.rdf4j.model.Statement::getObject);
            if (actionNode.isPresent() && actionNode.get() instanceof org.eclipse.rdf4j.model.Resource actionResource) {
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

            Map<String, String> keyTypeToValue = new java.util.HashMap<>();
            for (org.eclipse.rdf4j.model.Statement statement : model.filter(null, iri(KEY_VALUE), null)) {
                org.eclipse.rdf4j.model.Value keyNode = statement.getSubject();
                String keyValue = statement.getObject().stringValue();
                if (keyNode instanceof org.eclipse.rdf4j.model.Resource keyResource) {
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
            if (exit != null) {
                summary.add("exit=" + exit);
            }
            return summary.stream().collect(Collectors.joining(", "));
        }
    }
}
