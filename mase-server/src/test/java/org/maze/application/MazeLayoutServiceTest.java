package org.maze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.Test;
import org.maze.api.dto.CellDto;
import org.maze.api.dto.MazeLayoutDto;
import org.maze.domain.vocab.MazeVocab;

class MazeLayoutServiceTest {

    private static final String BASE = "http://127.0.1.1:8080";

    @Test
    void layoutUsesIncomingReferencesAndPlacesDisconnectedComponentsWithoutCcrsType() {
        SailRepository repository = new SailRepository(new MemoryStore());
        repository.init();

        try {
            ValueFactory vf = repository.getValueFactory();
            IRI maze = vf.createIRI(BASE + "/maze");
            IRI start = vf.createIRI(BASE + "/cells/a");
            IRI incomingOnly = vf.createIRI(BASE + "/cells/b");
            IRI disconnected = vf.createIRI(BASE + "/cells/c");
            IRI cellType = vf.createIRI(MazeVocab.MAZE_NS + "Cell");
            IRI smallMazeType = vf.createIRI(MazeVocab.MAZE_NS + "SmallMaze");
            IRI east = vf.createIRI(MazeVocab.MAZE_NS + "east");
            IRI startPredicate = vf.createIRI(MazeVocab.START);

            try (SailRepositoryConnection conn = repository.getConnection()) {
                conn.add(maze, RDF.TYPE, smallMazeType);
                conn.add(maze, startPredicate, start);
                conn.add(start, RDF.TYPE, cellType);
                conn.add(incomingOnly, RDF.TYPE, cellType);
                conn.add(disconnected, RDF.TYPE, cellType);
                conn.add(incomingOnly, east, start);
            }

            MazeLayoutDto layout = new MazeLayoutService(repository).getMazeLayout();
            Map<String, CellDto> cellsById = layout.cells.stream()
                    .collect(Collectors.toMap(cell -> cell.id, cell -> cell));
            CellDto startCell = cellsById.get(start.stringValue());
            CellDto incomingCell = cellsById.get(incomingOnly.stringValue());
            CellDto disconnectedCell = cellsById.get(disconnected.stringValue());

            assertNotNull(startCell);
            assertNotNull(incomingCell);
            assertNotNull(disconnectedCell);
            assertEquals(startCell.x - 1, incomingCell.x);
            assertEquals(startCell.y, incomingCell.y);

            Set<String> occupied = layout.cells.stream()
                    .map(cell -> cell.x + ":" + cell.y)
                    .collect(Collectors.toSet());
            assertEquals(layout.cells.size(), occupied.size());
        } finally {
            repository.shutDown();
        }
    }
}
