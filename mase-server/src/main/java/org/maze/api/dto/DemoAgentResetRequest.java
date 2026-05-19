package org.maze.api.dto;

public record DemoAgentResetRequest(
        String agentName,
        boolean preferGreenSignifiers) {
}
