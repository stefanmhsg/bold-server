package org.maze.application.tracking;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.maze.domain.model.RequestInfo;
import org.maze.domain.model.RequestKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detailed tracker for all agent requests to the maze server.
 * Logs operation type, access decision, and request repetition count.
 * Unlike MazePathTracker, this logs ALL requests including denied ones and re-requests.
 */
public class MazeRequestTracker {

    private static final Logger log = LoggerFactory.getLogger(MazeRequestTracker.class);
    
    // Track request counts per agent/cell/operation combination
    private final Map<RequestKey, RequestInfo> requestCounts = new ConcurrentHashMap<>();
    
    // Track last logged request per agent to detect changes
    private final Map<String, RequestKey> lastLoggedRequest = new ConcurrentHashMap<>();
    
    // Track which log files have been cleared in this server session
    private final Set<String> clearedLogFiles = ConcurrentHashMap.newKeySet();
    
    // Directory where detailed logs are stored
    private final String logDirectory;
    
    // Timestamp formatter for log entries
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                        .withZone(ZoneId.of("UTC"));
    
    /**
     * Create a new detailed request tracker.
     * @param logDirectory Directory where agent request logs will be stored.
     *                     If null or empty, defaults to "agent-requests"
     */
    public MazeRequestTracker(String logDirectory) {
        this.logDirectory = (logDirectory != null && !logDirectory.isEmpty()) 
            ? logDirectory 
            : "agent-requests";
        
        // Create directory if it doesn't exist
        try {
            Path dir = Paths.get(this.logDirectory);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created agent request tracking directory: {}", this.logDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to create request tracking directory: {}", this.logDirectory, e);
        }
    }
    
    /**
     * Record an agent's request (GET, POST, etc.) to a cell.
     * Tracks all requests including repeated ones and denied access.
     * 
     * @param agentName Name of the agent
     * @param cellUri URI of the cell being accessed
     * @param operation Operation type (e.g., "GET", "POST")
     * @param allowed Whether the access was allowed or denied
     */
    public void recordRequest(String agentName, String cellUri, String operation, boolean allowed) {
        if (agentName == null || agentName.trim().isEmpty()) {
            return;
        }
        
        RequestKey key = new RequestKey(agentName, cellUri, operation);
        
        // Update or create request info
        RequestInfo info = requestCounts.computeIfAbsent(key, k -> new RequestInfo());
        info.increment(allowed);
        
        // Check if this is a different request than the last logged one
        RequestKey lastRequest = lastLoggedRequest.get(agentName);
        boolean isDifferentRequest = lastRequest == null || !lastRequest.equals(key);
        
        if (isDifferentRequest) {
            // Different request - log the previous one (if exists) and start new count
            if (lastRequest != null) {
                RequestInfo lastInfo = requestCounts.get(lastRequest);
                if (lastInfo != null) {
                    writeRequestToLog(agentName, lastRequest.cellUri(), lastRequest.operation(), 
                                    lastInfo.getCount(), lastInfo.isLastAccessAllowed());
                }
            }
            
            // Update last logged request
            lastLoggedRequest.put(agentName, key);
            
            // Reset count for new request sequence
            info.setCount(1);
            info.setLastAccessAllowed(allowed);
        }
        // If same request, count is already incremented, will be logged when it changes
        
        log.debug("Recorded request for agent {}: {} {} (count: {}, allowed: {})", 
                 agentName, operation, cellUri, info.getCount(), allowed);
    }
    
    /**
     * Flush any pending request counts to the log.
     * Should be called periodically or when an agent session ends.
     * 
     * @param agentName Name of the agent to flush
     */
    public void flushAgent(String agentName) {
        RequestKey lastRequest = lastLoggedRequest.get(agentName);
        if (lastRequest != null) {
            RequestInfo info = requestCounts.get(lastRequest);
            if (info != null && info.getCount() > 0) {
                writeRequestToLog(agentName, lastRequest.cellUri(), lastRequest.operation(), 
                                info.getCount(), info.isLastAccessAllowed());
            }
        }
    }
    
    /**
     * Write a request entry to the agent's detailed log file.
     * Clears old log file if it exists on first write (per server session).
     */
    private void writeRequestToLog(String agentName, String cellUri, String operation,
                                   int count, boolean allowed) {
        String sanitizedAgentName = agentName.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path logFile = Paths.get(logDirectory, sanitizedAgentName + "-requests.log");
        
        // If log file exists and hasn't been cleared yet in this session, delete it
        if (!clearedLogFiles.contains(sanitizedAgentName)) {
            try {
                if (Files.exists(logFile)) {
                    Files.delete(logFile);
                    log.info("Deleted existing request log file for agent {} to start fresh session", agentName);
                }
            } catch (IOException e) {
                log.error("Failed to delete existing request log file for agent {}", agentName, e);
            }
            // Mark as cleared for this session
            clearedLogFiles.add(sanitizedAgentName);
        }
        
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
        String accessStatus = allowed ? "allowed" : "denied";
        String logEntry = String.format("%s, %s, %d, %s, %s%n", 
                                       cellUri, operation, count, accessStatus, timestamp);
        
        String logFileName = logFile.toString();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName, true))) {
            writer.write(logEntry);
            log.debug("Logged request for agent {}: {} {} x{} ({})", 
                     agentName, operation, cellUri, count, accessStatus);
        } catch (IOException e) {
            log.error("Failed to write request to log for agent {}: {}", agentName, cellUri, e);
        }
    }
    
    /**
     * Clear the tracking history for a specific agent.
     * 
     * @param agentName Name of the agent to reset
     */
    public void resetAgent(String agentName) {
        // Remove all request counts for this agent
        requestCounts.keySet().removeIf(key -> key.agentName().equals(agentName));
        lastLoggedRequest.remove(agentName);
        log.info("Reset request tracking for agent {}", agentName);
    }
    
    /**
     * Get the request log file name for an agent.
     * 
     * @param agentName Name of the agent
     * @return Path to the agent's request log file
     */
    public String getLogFileName(String agentName) {
        String sanitizedAgentName = agentName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return String.format("%s/%s-requests.log", logDirectory, sanitizedAgentName);
    }
}
