package org.maze.api.dto;

import java.util.List;
import java.util.Map;

public record DemoHttpTranscriptDto(
        String method,
        String url,
        Map<String, String> requestHeaders,
        String requestBody,
        int responseStatus,
        Map<String, List<String>> responseHeaders,
        String responseBody) {
}
