package org.maze.api.dto;

public record DemoAgentStepResponseDto(
        int step,
        String status,
        String agent,
        String currentCell,
        String phase,
        String decision,
        boolean complete,
        DemoHttpTranscriptDto transcript,
        String error) {
}
