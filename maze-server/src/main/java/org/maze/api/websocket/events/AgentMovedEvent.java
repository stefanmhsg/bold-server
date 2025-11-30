package org.maze.api.websocket.events;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.maze.domain.vocab.MazeVocab;

public class AgentMovedEvent extends MazeEvent {
    public String agent;
    public String cell;

    public AgentMovedEvent() {
        this.type = "AGENT_MOVED";
    }

    @Override
    public void processStatement(Statement st) {
        String pred = st.getPredicate().stringValue();
        String obj = st.getObject().stringValue();

        if (pred.equals(MazeVocab.AGENT)) {
            this.agent = obj;
        } else if (pred.equals(MazeVocab.TARGET_CELL)) {
            this.cell = obj;
        }
    }

    @Override
    public boolean isComplete() {
        return agent != null && cell != null;
    }
    
    public static boolean isRelevant(Statement st) {
        String pred = st.getPredicate().stringValue();
        return (st.getPredicate().equals(RDF.TYPE) && st.getObject().stringValue().equals(MazeVocab.MOVE_SUCCESS_EVENT)) ||
               pred.equals(MazeVocab.AGENT) ||
               pred.equals(MazeVocab.TARGET_CELL) ||
               pred.equals(MazeVocab.TIMESTAMP);
    }
}
