package org.maze.api.websocket.events;

import org.eclipse.rdf4j.model.Statement;

public abstract class MazeEvent {
    public String type;

    public abstract void processStatement(Statement st);
    public abstract boolean isComplete();
}
