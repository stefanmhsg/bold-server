package org.maze.api.websocket.events;

import org.eclipse.rdf4j.model.Statement;

public class UiDeleteEvent extends MazeEvent {
    public String id;

    public UiDeleteEvent(String subjectUri) {
        this.id = subjectUri;
        this.type = "UI_DELETE";
    }

    @Override
    public void processStatement(Statement st) {
        // No-op: UI delete events only need the element id.
    }

    @Override
    public boolean isComplete() {
        return id != null && !id.isBlank();
    }
}
