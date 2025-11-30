package org.maze.api.websocket.events;

import org.eclipse.rdf4j.model.Statement;
import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class MazeEvent {
    public String type;

    public abstract void processStatement(Statement st);
    
    @JsonIgnore
    public abstract boolean isComplete();
}
