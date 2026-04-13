package org.maze.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.dto.CellDto;
import org.maze.api.dto.ItemDto;
import org.maze.api.dto.LockDto;
import org.maze.api.dto.MazeLayoutDto;
import org.maze.api.dto.UiUpsertDto;
import org.maze.domain.vocab.MazeVocab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MazeLayoutService {

    private static final Logger log = LoggerFactory.getLogger(MazeLayoutService.class);
    private final SailRepository repository;

    private static final String NORTH = "north";
    private static final String SOUTH = "south";
    private static final String EAST = "east";
    private static final String WEST = "west";
    private static final String CCRS_MAZE_TYPE = "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#CcrsMaze";

    public MazeLayoutService(SailRepository repository) {
        this.repository = repository;
    }

    /**
     * Generate a snapshot of the current maze layout.
     * Queries all cells, their connections, items, and locks.
     * Calculates a 2D layout based on connections starting from the start cell.
     * @return MazeLayoutDto 
     */
    public MazeLayoutDto getMazeLayout() {
        MazeLayoutDto layout = new MazeLayoutDto();
        Map<String, CellDto> cellMap = new HashMap<>();
        String startCell = null;
        String exitCell = null;
        boolean useCcrsLayout = false;

        try (SailRepositoryConnection conn = repository.getConnection()) {
            useCcrsLayout = isCcrsMaze(conn);

            // 1. Find Start Cell
            String startQuery = "PREFIX xhv: <http://www.w3.org/1999/xhtml/vocab#> " +
                              "SELECT ?start WHERE { ?s xhv:start ?start } LIMIT 1";
            try (TupleQueryResult result = conn.prepareTupleQuery(startQuery).evaluate()) {
                if (result.hasNext()) {
                    startCell = result.next().getValue("start").stringValue();
                }
            }

            // 2. Fetch All Cells and Properties
            String cellQuery = 
                "PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> " +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#> " +
                "PREFIX http: <http://www.w3.org/2011/http#> " +
                "SELECT DISTINCT ?cell ?label ?north ?south ?east ?west ?exit ?keyVal ?lockState ?isLock ?keyNeeded ?green WHERE { " +
                "    ?cell a maze:Cell . " +
                "    OPTIONAL { ?cell rdfs:label ?label } " +
                "    OPTIONAL { ?cell maze:north ?north } " +
                "    OPTIONAL { ?cell maze:south ?south } " +
                "    OPTIONAL { ?cell maze:east ?east } " +
                "    OPTIONAL { ?cell maze:west ?west } " +
                "    OPTIONAL { ?cell maze:exit ?exit } " +
                "    OPTIONAL { ?cell maze:green ?green } " +
                "    OPTIONAL { " +
                "       { ?cell dyn:hasKey ?k . ?k dyn:keyValue ?keyVal . } " +
                "       UNION " +
                "       { GRAPH ?cell { ?k dyn:keyValue ?keyVal . } } " +
                "    } " +
                "    OPTIONAL { " +
                "        ?cell a dyn:Lock . " +
                "        BIND('true' as ?isLock) " +
                "        OPTIONAL { ?cell dyn:state ?lockState . } " +
                "        OPTIONAL { ?cell dyn:needsAction/http:body/dyn:foundAt ?keyNeeded . } " +
                "    } " +
                "}";

            TupleQuery query = conn.prepareTupleQuery(cellQuery);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    String id = bs.getValue("cell").stringValue();
                    
                    CellDto cell = cellMap.computeIfAbsent(id, k -> {
                        CellDto c = new CellDto();
                        c.id = k;
                        c.connections = new HashMap<>();
                        c.items = new ArrayList<>();
                        return c;
                    });

                    if (bs.hasBinding("label")) cell.label = bs.getValue("label").stringValue();
                    if (bs.hasBinding("north")) cell.connections.put("north", normalize(bs.getValue("north").stringValue()));
                    if (bs.hasBinding("south")) cell.connections.put("south", normalize(bs.getValue("south").stringValue()));
                    if (bs.hasBinding("east")) cell.connections.put("east", normalize(bs.getValue("east").stringValue()));
                    if (bs.hasBinding("west")) cell.connections.put("west", normalize(bs.getValue("west").stringValue()));
                    if (bs.hasBinding("exit")) {
                        String exitUri = normalize(bs.getValue("exit").stringValue());
                        exitCell = exitUri;
                        cell.connections.put("exit", exitUri);
                    }

                    if (bs.hasBinding("green")) {
                        cell.connections.put("green", normalize(bs.getValue("green").stringValue()));
                    }

                    if (bs.hasBinding("keyVal")) {
                        String keyVal = bs.getValue("keyVal").stringValue();
                        // Avoid duplicates if multiple rows returned for same cell
                        boolean exists = cell.items.stream().anyMatch(i -> i.value.equals(keyVal));
                        if (!exists) {
                            cell.items.add(new ItemDto("KEY", keyVal));
                        }
                    }

                    if (bs.hasBinding("isLock")) {
                        boolean isLocked = true; // Default to locked
                        if (bs.hasBinding("lockState")) {
                            String state = bs.getValue("lockState").stringValue();
                            isLocked = state.endsWith("locked");
                        }
                        
                        String keyNeeded = null;
                        if (bs.hasBinding("keyNeeded")) {
                            String keyUri = bs.getValue("keyNeeded").stringValue();
                            // Extract simple name (e.g. RedKey)
                            keyNeeded = keyUri.contains("#") 
                                ? keyUri.substring(keyUri.lastIndexOf('#') + 1)
                                : keyUri.substring(keyUri.lastIndexOf('/') + 1);
                        }
                        
                        cell.lock = new LockDto(isLocked, keyNeeded);
                    }
                }
            }
        }
        // Keep the logical exit target for movement semantics, but do not expose it
        // as a drawable maze cell in the admin layout snapshot.
        if (exitCell != null) {
            cellMap.remove(exitCell);
        }

        if (startCell != null && cellMap.containsKey(startCell)) {
            if (useCcrsLayout) {
                calculateCcrsLayout(cellMap, startCell);
            } else {
                calculateLayout(cellMap, startCell);
            }
        } else {
            log.warn("Start cell not found or not in cell map");
        }

        // 4. Normalize and Finalize
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (CellDto cell : cellMap.values()) {
            minX = Math.min(minX, cell.x);
            minY = Math.min(minY, cell.y);
            maxX = Math.max(maxX, cell.x);
            maxY = Math.max(maxY, cell.y);
        }

        for (CellDto cell : cellMap.values()) {
            cell.x -= minX;
            cell.y -= minY;
            
            // Fill missing connections as "wall"
            cell.connections.putIfAbsent("north", "wall");
            cell.connections.putIfAbsent("south", "wall");
            cell.connections.putIfAbsent("east", "wall");
            cell.connections.putIfAbsent("west", "wall");
        }

        layout.width = (maxX - minX) + 1;
        layout.height = (maxY - minY) + 1;
        layout.startCell = startCell;
        layout.exitCell = exitCell;
        layout.cells = new ArrayList<>(cellMap.values());

        return layout;
    }

    private void calculateCcrsLayout(Map<String, CellDto> cellMap, String startCellId) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, List<IncomingReference>> incomingByTarget = buildIncomingIndex(cellMap);
        Map<String, String> occupiedPositions = new HashMap<>();
        
        CellDto start = cellMap.get(startCellId);
        start.x = 0;
        start.y = 0;
        
        queue.add(startCellId);
        visited.add(startCellId);
        occupiedPositions.put(positionKey(start.x, start.y), startCellId);

        expandPlacedCells(cellMap, visited, queue, incomingByTarget, occupiedPositions);

        int nextComponentX = maxX(cellMap, visited) + 3;
        for (String cellId : cellMap.keySet()) {
            if (visited.contains(cellId)) {
                continue;
            }

            CellDto seed = cellMap.get(cellId);
            seed.x = nextComponentX;
            seed.y = 0;

            visited.add(cellId);
            queue.add(cellId);
            occupiedPositions.put(positionKey(seed.x, seed.y), cellId);

            expandPlacedCells(cellMap, visited, queue, incomingByTarget, occupiedPositions);
            nextComponentX = maxX(cellMap, visited) + 3;
        }
    }

    private void calculateLayout(Map<String, CellDto> cellMap, String startCellId) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        
        CellDto start = cellMap.get(startCellId);
        start.x = 0;
        start.y = 0;
        
        queue.add(startCellId);
        visited.add(startCellId);
        
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            CellDto current = cellMap.get(currentId);
            
            processNeighbor(current, "north", 0, -1, cellMap, visited, queue);
            processNeighbor(current, "south", 0, 1, cellMap, visited, queue);
            processNeighbor(current, "east", 1, 0, cellMap, visited, queue);
            processNeighbor(current, "west", -1, 0, cellMap, visited, queue);
        }
    }

    private void expandPlacedCells(
            Map<String, CellDto> cellMap,
            Set<String> visited,
            Queue<String> queue,
            Map<String, List<IncomingReference>> incomingByTarget,
            Map<String, String> occupiedPositions) {

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            CellDto current = cellMap.get(currentId);

            processCcrsNeighbor(current, NORTH, 0, -1, cellMap, visited, queue, occupiedPositions);
            processCcrsNeighbor(current, SOUTH, 0, 1, cellMap, visited, queue, occupiedPositions);
            processCcrsNeighbor(current, EAST, 1, 0, cellMap, visited, queue, occupiedPositions);
            processCcrsNeighbor(current, WEST, -1, 0, cellMap, visited, queue, occupiedPositions);
            processIncomingNeighbors(current, cellMap, visited, queue, incomingByTarget, occupiedPositions);
        }
    }

    private void processNeighbor(CellDto current, String direction, int dx, int dy, 
                               Map<String, CellDto> cellMap, Set<String> visited, Queue<String> queue) {
        String neighborId = current.connections.get(direction);
        if (neighborId != null && !neighborId.equals("wall") && !neighborId.contains("Wall")) {
            if (!visited.contains(neighborId) && cellMap.containsKey(neighborId)) {
                CellDto neighbor = cellMap.get(neighborId);
                neighbor.x = current.x + dx;
                neighbor.y = current.y + dy;
                visited.add(neighborId);
                queue.add(neighborId);
            }
        }
    }

    private void processCcrsNeighbor(CellDto current, String direction, int dx, int dy,
                               Map<String, CellDto> cellMap, Set<String> visited, Queue<String> queue,
                               Map<String, String> occupiedPositions) {
        String neighborId = current.connections.get(direction);
        if (neighborId != null && !neighborId.equals("wall") && !neighborId.contains("Wall")) {
            if (!visited.contains(neighborId) && cellMap.containsKey(neighborId)) {
                CellDto neighbor = cellMap.get(neighborId);
                placeCell(neighbor, neighborId, current.x + dx, current.y + dy, visited, queue, occupiedPositions);
            }
        }
    }

    private void processIncomingNeighbors(
            CellDto current,
            Map<String, CellDto> cellMap,
            Set<String> visited,
            Queue<String> queue,
            Map<String, List<IncomingReference>> incomingByTarget,
            Map<String, String> occupiedPositions) {
        List<IncomingReference> incoming = incomingByTarget.getOrDefault(current.id, List.of());
        for (IncomingReference ref : incoming) {
            if (visited.contains(ref.sourceId)) {
                continue;
            }

            CellDto source = cellMap.get(ref.sourceId);
            if (source == null) {
                continue;
            }

            int x = current.x - dxFor(ref.direction);
            int y = current.y - dyFor(ref.direction);
            placeCell(source, ref.sourceId, x, y, visited, queue, occupiedPositions);
        }
    }

    private void placeCell(
            CellDto cell,
            String cellId,
            int x,
            int y,
            Set<String> visited,
            Queue<String> queue,
            Map<String, String> occupiedPositions) {
        String key = positionKey(x, y);
        String occupiedBy = occupiedPositions.get(key);
        if (occupiedBy != null && !occupiedBy.equals(cellId)) {
            log.warn("Skipping layout placement for {} at ({}, {}) because position is already occupied by {}", cellId, x, y, occupiedBy);
            return;
        }

        cell.x = x;
        cell.y = y;
        visited.add(cellId);
        queue.add(cellId);
        occupiedPositions.put(key, cellId);
    }

    private Map<String, List<IncomingReference>> buildIncomingIndex(Map<String, CellDto> cellMap) {
        Map<String, List<IncomingReference>> incomingByTarget = new HashMap<>();
        for (CellDto cell : cellMap.values()) {
            addIncomingReference(incomingByTarget, cellMap, cell, NORTH);
            addIncomingReference(incomingByTarget, cellMap, cell, SOUTH);
            addIncomingReference(incomingByTarget, cellMap, cell, EAST);
            addIncomingReference(incomingByTarget, cellMap, cell, WEST);
        }
        return incomingByTarget;
    }

    private void addIncomingReference(
            Map<String, List<IncomingReference>> incomingByTarget,
            Map<String, CellDto> cellMap,
            CellDto cell,
            String direction) {
        String targetId = cell.connections.get(direction);
        if (targetId == null || targetId.equals("wall") || targetId.contains("Wall") || !cellMap.containsKey(targetId)) {
            return;
        }
        incomingByTarget
                .computeIfAbsent(targetId, ignored -> new ArrayList<>())
                .add(new IncomingReference(cell.id, direction));
    }

    private int maxX(Map<String, CellDto> cellMap, Set<String> visited) {
        int maxX = 0;
        for (String cellId : visited) {
            CellDto cell = cellMap.get(cellId);
            if (cell != null) {
                maxX = Math.max(maxX, cell.x);
            }
        }
        return maxX;
    }

    private int dxFor(String direction) {
        return switch (direction) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    private int dyFor(String direction) {
        return switch (direction) {
            case NORTH -> -1;
            case SOUTH -> 1;
            default -> 0;
        };
    }

    private String positionKey(int x, int y) {
        return x + ":" + y;
    }

    private boolean isCcrsMaze(SailRepositoryConnection conn) {
        String query =
                "SELECT ?type WHERE { <http://127.0.1.1:8080/maze> a ?type . }";
        try (TupleQueryResult result = conn.prepareTupleQuery(query).evaluate()) {
            while (result.hasNext()) {
                BindingSet bs = result.next();
                if (CCRS_MAZE_TYPE.equals(bs.getValue("type").stringValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getMazeScenarioName() {
        String query =
                "SELECT ?type WHERE { <http://127.0.1.1:8080/maze> a ?type . }";

        try (SailRepositoryConnection conn = repository.getConnection();
             TupleQueryResult result = conn.prepareTupleQuery(query).evaluate()) {
            while (result.hasNext()) {
                BindingSet bs = result.next();
                String type = bs.getValue("type").stringValue();
                if (type.endsWith("#BasicContainer") || type.endsWith("#Container")) {
                    continue;
                }
                return localName(type);
            }
        }

        return null;
    }

    private String localName(String iri) {
        int hashIdx = iri.lastIndexOf('#');
        int slashIdx = iri.lastIndexOf('/');
        int idx = Math.max(hashIdx, slashIdx);
        if (idx < 0 || idx + 1 >= iri.length()) {
            return iri;
        }
        return iri.substring(idx + 1);
    }

    private static final class IncomingReference {
        private final String sourceId;
        private final String direction;

        private IncomingReference(String sourceId, String direction) {
            this.sourceId = sourceId;
            this.direction = direction;
        }
    }

    private String normalize(String uri) {
        return (uri != null && uri.endsWith("Wall")) ? "wall" : uri;
    }

    /**
     * Generate a snapshot of all UI elements in the maze.
     * Follows same pattern as UiUpsertEvents being sent to MazeViewer.
     * On startup or reload, this can fetch current UI state for rendering.
     * @return List of UiUpsertDto representing current UI elements to be rendered.
     */
    public List<UiUpsertDto> getUiSnapshot() {
    List<UiUpsertDto> result = new ArrayList<>();

        try (SailRepositoryConnection conn = repository.getConnection()) {
            String namespace = MazeVocab.UI_NS;
            String q =
                "PREFIX ui: <" + namespace + "> " +
                "SELECT ?ui ?p ?o ?layer ?konvaType WHERE { " +
                "  ?cell ui:hasUiElement ?ui . " +
                "  OPTIONAL { ?ui ui:konvaType ?konvaType } " +
                "  OPTIONAL { ?ui ui:layer ?layer } " +
                "  ?ui ?p ?o . " +
                "}";

            TupleQuery tq = conn.prepareTupleQuery(q);
            try (TupleQueryResult r = tq.evaluate()) {

                Map<String, UiUpsertDto> byId = new HashMap<>();

                while (r.hasNext()) {
                    BindingSet bs = r.next();

                    String id = bs.getValue("ui").stringValue();
                    UiUpsertDto dto = byId.computeIfAbsent(id, k -> {
                        UiUpsertDto u = new UiUpsertDto();
                        u.id = k;
                        u.layer = bs.hasBinding("layer")
                            ? bs.getValue("layer").stringValue()
                            : "overlay";
                        u.konvaType = bs.hasBinding("konvaType")
                            ? bs.getValue("konvaType").stringValue()
                            : null;
                        return u;
                    });

                    String pred = bs.getValue("p").stringValue();
                    String obj = bs.getValue("o").stringValue();

                    if (pred.startsWith(MazeVocab.UI_NS)) {
                        String attr = pred.substring(MazeVocab.UI_NS.length());
                        dto.attrs.put(attr, parseLiteral(obj));
                    }
                }

                result.addAll(byId.values());
            }
        }

        return result;
    }

    private Object parseLiteral(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e1) {
            try {
                return Double.valueOf(value);
            } catch (NumberFormatException e2) {
                return value;
            }
        }
    }


}
