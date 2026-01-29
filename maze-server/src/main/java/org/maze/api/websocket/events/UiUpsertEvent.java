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

        // What Konva node should be created
        if (pred.equals(MazeVocab.UI_TYPE)) {
            this.konvaType = obj;
            if (obj.equals("Arrow")) {
                // Arrows have default attributes
                attrs.put("points", new int[] {-15, 0, 15, 0}); 
                attrs.put("pointerLength", 8);
                attrs.put("pointerWidth", 8);
            }
            return;
        }

        // Cardinal direction (N/E/S/W) to rotation angle
        if (pred.equals(MazeVocab.UI_DIRECTION)) {
            attrs.put("rotation", rotationFromDirection(obj));
            return;
        }

        // Generic UI attributes (x, y, width, fill, rotation, ...)
        if (pred.startsWith(MazeVocab.UI_NS)) {
            String attr = pred.substring(MazeVocab.UI_NS.length());
            attrs.put(attr, obj);
            return;
        }
    }

    private int rotationFromDirection(String dir) {
        return switch (dir) {
            case "N" -> 270;
            case "E" -> 0;
            case "S" -> 90;
            case "W" -> 180;
            default -> 0;
        };
    }

    @Override
    public boolean isComplete() {
        return konvaType != null && !attrs.isEmpty();
    }

     public static boolean isRelevant(Statement st) {
        String pred = st.getPredicate().stringValue();
        return pred.startsWith(MazeVocab.UI_NS);
    }   
}
