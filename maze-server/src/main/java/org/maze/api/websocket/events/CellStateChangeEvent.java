package org.maze.api.websocket.events;

import org.eclipse.rdf4j.model.Statement;
import org.maze.domain.vocab.MazeVocab;

public class CellStateChangeEvent extends MazeEvent {
    public String cell;
    public boolean isLocked;

    public CellStateChangeEvent(String cellUri) {
        this.cell = cellUri;
        this.type = "CELL_UNKNOWN";
    }

    @Override
    public void processStatement(Statement st) {
        if (st.getPredicate().stringValue().equals(MazeVocab.STATE)) {
            String state = st.getObject().stringValue();
            if (state.endsWith("locked")) {
                this.isLocked = true;
                this.type = "CELL_LOCKED";
            } else if (state.endsWith("unlocked")) {
                this.isLocked = false;
                this.type = "CELL_UNLOCKED";
            }
        }
    }

    @Override
    public boolean isComplete() {
        return !type.equals("CELL_UNKNOWN");
    }
}
