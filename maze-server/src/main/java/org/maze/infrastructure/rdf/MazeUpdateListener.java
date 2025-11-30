package org.maze.infrastructure.rdf;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.maze.api.websocket.MazeBroadcaster;
import org.maze.api.websocket.events.AgentMovedEvent;
import org.maze.api.websocket.events.CellStateChangeEvent;
import org.maze.api.websocket.events.MazeEvent;
import org.maze.domain.vocab.MazeVocab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Connection level listener that reacts to individual statement changes.
 * Aggregates statements for blank node events and broadcasts them on commit.
 */
public class MazeUpdateListener implements SailConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(MazeUpdateListener.class);
    
    // Buffer for pending events: Subject URI/BNode -> Event Object
    private final Map<String, MazeEvent> pendingEvents = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void statementAdded(Statement st, boolean inferred) {
        // We only care about explicit statements for event generation
        if (inferred) return;

        log.debug("[NOTIFYING SAIL] ADDED: {} {} {}", st.getSubject(), st.getPredicate(), st.getObject());

        String subjectKey = st.getSubject().stringValue();
        MazeEvent event = pendingEvents.get(subjectKey);

        if (event != null) {
            event.processStatement(st);
        } else {
            // Try to detect new event
            if (AgentMovedEvent.isRelevant(st)) {
                event = new AgentMovedEvent();
                event.processStatement(st);
                pendingEvents.put(subjectKey, event);
            } else if (st.getPredicate().stringValue().equals(MazeVocab.STATE)) {
                event = new CellStateChangeEvent(subjectKey);
                event.processStatement(st);
                pendingEvents.put(subjectKey, event);
            }
        }
    }

    @Override
    public void statementRemoved(Statement st, boolean inferred) {
        log.debug("[NOTIFYING SAIL] REMOVED: {} {} {}", st.getSubject(), st.getPredicate(), st.getObject());
    }

    // Called by the ConnectionWrapper after a successful commit
    public void onCommit() {
        for (MazeEvent event : pendingEvents.values()) {
            if (event.isComplete()) {
                broadcastEvent(event);
            }
        }
        pendingEvents.clear();
    }

    // Called by the ConnectionWrapper on rollback
    public void onRollback() {
        pendingEvents.clear();
    }

    private void broadcastEvent(MazeEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            log.info("Broadcasting Event: {}", json);
            MazeBroadcaster.broadcast(json);
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
        }
    }

    // OLD deprecated method (still required!)
    @Override
    @Deprecated
    public void statementAdded(Statement st) {
        statementAdded(st, false);
    }

    // OLD deprecated method (still required!)
    @Override
    @Deprecated
    public void statementRemoved(Statement st) {
        statementRemoved(st, false);
    }
}
