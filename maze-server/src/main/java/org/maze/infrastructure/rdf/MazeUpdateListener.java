package org.maze.infrastructure.rdf;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.maze.domain.vocab.MazeVocab;
import org.maze.api.websocket.MazeBroadcaster;

/**
 * Connection level listener that reacts to individual statement changes.
 * For now it only logs agent moves but is ready to forward to a broadcaster.
 */
public class MazeUpdateListener implements SailConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(MazeUpdateListener.class);

    // Adjust to your actual maze vocabulary
    private static final String MOVE_SUCCEEDED = MazeVocab.DYNMAZE_NS + "moveSucceeded";

    // NEW (preferred) method
    @Override
    public void statementAdded(Statement st, boolean inferred) {
        log.info("[NOTIFYING SAIL] ADDED: {} {} {} (inferred={})",
            st.getSubject(),
            st.getPredicate(),
            st.getObject(),
            inferred);

        if (isMovement(st)) {
            processMovement(st, inferred);
        }
    }

    // NEW (preferred) method
    @Override
    public void statementRemoved(Statement st, boolean inferred) {
        log.info("[NOTIFYING SAIL] REMOVED: {} {} {} (inferred={})",
            st.getSubject(),
            st.getPredicate(),
            st.getObject(),
            inferred);
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


    private boolean isMovement(Statement st) {
        return st.getPredicate().stringValue()
                .equals(MOVE_SUCCEEDED);
    }

    private void processMovement(Statement st, boolean inferred) {
        String agent = st.getSubject().stringValue();
        String cell = st.getObject().stringValue();

        log.debug("Agent {} moved to {} (inferred={})", agent, cell, inferred);

        // Broadcast event via WebSocket
        String json = String.format("{\"type\": \"AGENT_MOVED\", \"agent\": \"%s\", \"cell\": \"%s\"}", 
            agent, cell);
        MazeBroadcaster.broadcast(json);
    }

}
