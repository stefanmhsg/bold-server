package org.maze.infrastructure.rdf;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.maze.domain.vocab.MazeVocab;
import org.maze.api.websocket.MazeBroadcaster;

/**
 * Connection level listener that reacts to individual statement changes.
 * Aggregates statements for blank node events and broadcasts them on commit.
 */
public class MazeUpdateListener implements SailConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(MazeUpdateListener.class);
    
    // Buffer for pending events: Subject (BlankNode) -> Event Data
    private final Map<Resource, Map<String, String>> pendingEvents = new HashMap<>();

    @Override
    public void statementAdded(Statement st, boolean inferred) {
        // We only care about explicit statements for event generation
        if (inferred) return;

        log.debug("[NOTIFYING SAIL] ADDED: {} {} {}", st.getSubject(), st.getPredicate(), st.getObject());

        Resource subject = st.getSubject();
        IRI predicate = st.getPredicate();
        Value object = st.getObject();

        // Check if this statement is part of a MoveSuccessEvent
        if (isMoveEventStatement(st)) {
            pendingEvents.computeIfAbsent(subject, k -> new HashMap<>())
                         .put(predicate.stringValue(), object.stringValue());
            
            // If it's the type declaration, mark it explicitly
            if (predicate.equals(RDF.TYPE)) {
                pendingEvents.get(subject).put("@type", object.stringValue());
            }
        }
    }

    @Override
    public void statementRemoved(Statement st, boolean inferred) {
        log.debug("[NOTIFYING SAIL] REMOVED: {} {} {}", st.getSubject(), st.getPredicate(), st.getObject());
    }

    // Called by the ConnectionWrapper after a successful commit
    public void onCommit() {
        for (Map.Entry<Resource, Map<String, String>> entry : pendingEvents.entrySet()) {
            Map<String, String> properties = entry.getValue();
            
            // Only broadcast if it is actually a MoveSuccessEvent
            if (isCompleteEvent(properties)) {
                broadcastEvent(properties);
            }
        }
        pendingEvents.clear();
    }

    // Called by the ConnectionWrapper on rollback
    public void onRollback() {
        pendingEvents.clear();
    }

    private boolean isMoveEventStatement(Statement st) {
        String pred = st.getPredicate().stringValue();
        // Check for Type definition or specific properties
        return (st.getPredicate().equals(RDF.TYPE) && st.getObject().stringValue().equals(MazeVocab.MOVE_SUCCESS_EVENT)) ||
               pred.equals(MazeVocab.AGENT) ||
               pred.equals(MazeVocab.TARGET_CELL) ||
               pred.equals(MazeVocab.TIMESTAMP);
    }

    private boolean isCompleteEvent(Map<String, String> props) {
        // Check if it has the correct type (or at least the required properties)
        String type = props.get("@type");
        return MazeVocab.MOVE_SUCCESS_EVENT.equals(type) && 
               props.containsKey(MazeVocab.AGENT) && 
               props.containsKey(MazeVocab.TARGET_CELL);
    }

    private void broadcastEvent(Map<String, String> props) {
        String agent = props.get(MazeVocab.AGENT);
        String cell = props.get(MazeVocab.TARGET_CELL);
        
        log.info("Broadcasting MoveSuccessEvent for agent {} to {}", agent, cell);
        
        String json = String.format("{\"type\": \"AGENT_MOVED\", \"agent\": \"%s\", \"cell\": \"%s\"}", 
            agent, cell);
        MazeBroadcaster.broadcast(json);
    }

    // OLD deprecated method (still required!)
    @Override
    @Deprecated
    public void statementAdded(Statement st) {
        // delegate to new method, assume explicit = false
        statementAdded(st, false);
    }

    // OLD deprecated method (still required!)
    @Override
    @Deprecated
    public void statementRemoved(Statement st) {
        statementRemoved(st, false);
    }
}
