package org.maze.api.dto;

public class ItemDto {
    public String type; // e.g., "KEY"
    public String value; // e.g., "redkey"
    
    public ItemDto() {}
    
    public ItemDto(String type, String value) {
        this.type = type;
        this.value = value;
    }
}
