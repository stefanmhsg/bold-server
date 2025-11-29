package org.maze.api.dto;

import java.util.List;
import java.util.Map;

public class MazeLayoutDto {
    public int width;
    public int height;
    public String startCell;
    public String exitCell;
    public List<CellDto> cells;
}
