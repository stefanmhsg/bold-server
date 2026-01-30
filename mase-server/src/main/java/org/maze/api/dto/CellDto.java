package org.maze.api.dto;

import java.util.List;
import java.util.Map;

public class CellDto {
    public String id;
    public int x;
    public int y;
    public String label;
    // Map keys: "north", "south", "east", "west". Values: Cell URI or "wall"
    public Map<String, String> connections; 
    public List<ItemDto> items;
    public LockDto lock;
}
