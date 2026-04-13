package org.maze.infrastructure.rdf;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.maze.api.websocket.MazeBroadcaster;
import org.maze.api.websocket.events.AgentMovedEvent;
import org.maze.api.websocket.events.UiDeleteEvent;
import org.maze.api.websocket.events.UiUpsertEvent;
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
    private static final String UI_DELETE_KEY_PREFIX = "ui-delete:"; // Distinct prefix to separate delete events in the pendingEvents map
    
    // Buffer for pending events: Subject URI/BNode -> Event Object
    private final Map<String, MazeEvent> pendingEvents = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void statementAdded(Statement st, boolean inferred) {
        // We only care about explicit statements for event generation
        if (inferred) return;

        log.debug("[NOTIFYING SAIL] ADDED: {} {} {}", st.getSubject(), st.getPredicate(), st.getObject());

        String subjectKey = st.getSubject().stringValue();
        String object = st.getObject().stringValue();

        // Cancel pending UI deletion as soon as the element appears again in this transaction.
        pendingEvents.remove(uiDeleteKey(subjectKey));
        pendingEvents.remove(uiDeleteKey(object));

        MazeEvent event = pendingEvents.get(subjectKey);

        // Update existing event if present
        if (event != null) {
            event.processStatement(st);
            return;
        }

        // Try to detect new event
        if (AgentMovedEvent.isRelevant(st)) {
            event = new AgentMovedEvent();
            event.processStatement(st);
            pendingEvents.put(subjectKey, event);
            return;
        } 

        // UI state projection (locks, arrows, items, etc.)
        if (UiUpsertEvent.isRelevant(st)) {
            event = new UiUpsertEvent(subjectKey);
            event.processStatement(st);
            pendingEvents.put(subjectKey, event);
            return;
        }

        return;
    }

    @Override
    public void statementRemoved(Statement st, boolean inferred) {
        if (inferred) return;

        String subjectKey = st.getSubject().stringValue();
        String predicate = st.getPredicate().stringValue();
        String object = st.getObject().stringValue();

        log.debug("[NOTIFYING SAIL] REMOVED: {} {} {}", subjectKey, predicate, object);

        // Case 1: Link removal <cell> ui:hasUiElement <...#ui-...>
        if (predicate.equals(MazeVocab.UI_HAS_UI_ELEMENT) && object.contains("#ui")) {
            pendingEvents.remove(object);
            pendingEvents.put(uiDeleteKey(object), new UiDeleteEvent(object));
            return;
        }

        // Case 2: Property removal on ui node <...#ui-...> ui:* ...
        if (subjectKey.contains("#ui") && predicate.startsWith(MazeVocab.UI_NS)) {
            pendingEvents.remove(subjectKey);
            pendingEvents.put(uiDeleteKey(subjectKey), new UiDeleteEvent(subjectKey));
        }
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

    private String uiDeleteKey(String uiElementId) {
        return UI_DELETE_KEY_PREFIX + uiElementId;
    }

    private void broadcastEvent(MazeEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            log.debug("Broadcasting Event: {}", json);
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
