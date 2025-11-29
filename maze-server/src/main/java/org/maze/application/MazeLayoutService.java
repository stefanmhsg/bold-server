package org.maze.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.dto.CellDto;
import org.maze.api.dto.ItemDto;
import org.maze.api.dto.LockDto;
import org.maze.api.dto.MazeLayoutDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MazeLayoutService {

    private static final Logger log = LoggerFactory.getLogger(MazeLayoutService.class);
    private final SailRepository repository;

    public MazeLayoutService(SailRepository repository) {
        this.repository = repository;
    }

    public MazeLayoutDto getMazeLayout() {
        MazeLayoutDto layout = new MazeLayoutDto();
        Map<String, CellDto> cellMap = new HashMap<>();
        String startCell = null;
        String exitCell = null;

        try (SailRepositoryConnection conn = repository.getConnection()) {
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
                "SELECT DISTINCT ?cell ?label ?north ?south ?east ?west ?exit ?keyVal ?lockState WHERE { " +
                "    ?cell a maze:Cell . " +
                "    OPTIONAL { ?cell rdfs:label ?label } " +
                "    OPTIONAL { ?cell maze:north ?north } " +
                "    OPTIONAL { ?cell maze:south ?south } " +
                "    OPTIONAL { ?cell maze:east ?east } " +
                "    OPTIONAL { ?cell maze:west ?west } " +
                "    OPTIONAL { ?cell maze:exit ?exit } " +
                "    OPTIONAL { ?cell dyn:hasKey ?k . ?k dyn:keyValue ?keyVal . } " +
                "    OPTIONAL { ?cell a dyn:Lock . ?cell dyn:state ?lockState . } " +
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

                    if (bs.hasBinding("keyVal")) {
                        String keyVal = bs.getValue("keyVal").stringValue();
                        // Avoid duplicates if multiple rows returned for same cell
                        boolean exists = cell.items.stream().anyMatch(i -> i.value.equals(keyVal));
                        if (!exists) {
                            cell.items.add(new ItemDto("KEY", keyVal));
                        }
                    }

                    if (bs.hasBinding("lockState")) {
                        String state = bs.getValue("lockState").stringValue();
                        boolean isLocked = state.endsWith("locked");
                        cell.lock = new LockDto(isLocked, null); // Key needed logic requires more complex query
                    }
                }
            }
        }

        // 3. BFS for Layout
        if (startCell != null && cellMap.containsKey(startCell)) {
            calculateLayout(cellMap, startCell);
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

    private String normalize(String uri) {
        return (uri != null && uri.endsWith("Wall")) ? "wall" : uri;
    }
}
