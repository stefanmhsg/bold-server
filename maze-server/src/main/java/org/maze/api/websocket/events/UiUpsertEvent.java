package org.maze.api.websocket.events;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.maze.domain.vocab.MazeVocab;

public class UiUpsertEvent extends MazeEvent {
    public String id;
    public String layer = "overlay";
    public String konvaType; // Konva type, e.g. Rect, Arrow, Circle
    public Map<String, Object> attrs = new HashMap<>();

    public UiUpsertEvent(String subjectUri) {
        this.id = subjectUri;
        this.type = "UI_UPSERT";
    }

    @Override
    public void processStatement(Statement st) {

        String pred = st.getPredicate().stringValue();
        String obj = st.getObject().stringValue();

        
        if (pred.equals(MazeVocab.UI_TYPE)) {
            this.konvaType = obj;
            return;
        }

        // Generic UI attributes (x, y, width, fill, rotation, direction, anchor, offsetX, offsetY, ...)
        if (pred.startsWith(MazeVocab.UI_NS)) {
            String attr = pred.substring(MazeVocab.UI_NS.length());
            attrs.put(attr, obj);
            return;
        }
    }

    @Override
    public boolean isComplete() {
        return !attrs.isEmpty();
    }

     public static boolean isRelevant(Statement st) {
        String pred = st.getPredicate().stringValue();
        return pred.startsWith(MazeVocab.UI_NS);
    }   
}
