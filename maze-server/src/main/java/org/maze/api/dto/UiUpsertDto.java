package org.maze.api.dto;

import java.util.HashMap;
import java.util.Map;

public class UiUpsertDto {
    public String id;
    public String layer;
    public String konvaType; // from Konva: e.g., "Rect", "Arrow", "Item"
    public Map<String, Object> attrs = new HashMap<>();
}
