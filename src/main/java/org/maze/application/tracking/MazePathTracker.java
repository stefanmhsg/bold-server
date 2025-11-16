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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks agent movement paths through the maze.
 * Creates a log file per agent containing timestamps and cell URIs for each movement.
 * Only tracks actual movements (not re-requests of the current cell).
 */
public class MazePathTracker {

    private static final Logger log = LoggerFactory.getLogger(MazePathTracker.class);
    
    // Track last visited cell per agent to detect actual movements
    private final Map<String, String> lastVisitedCell = new ConcurrentHashMap<>();
    
    // Track which log files have been cleared in this server session
    private final Set<String> clearedLogFiles = ConcurrentHashMap.newKeySet();
    
    // Directory where path logs are stored
    private final String pathLogDirectory;
    
    // Timestamp formatter for log entries
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                        .withZone(ZoneId.of("UTC"));
    
    /**
     * Create a new path tracker.
     * @param logDirectory Directory where agent path logs will be stored. 
     *                     If null or empty, defaults to "agent-paths"
     */
    public MazePathTracker(String logDirectory) {
        this.pathLogDirectory = (logDirectory != null && !logDirectory.isEmpty()) 
            ? logDirectory 
            : "agent-paths";
        
        // Create directory if it doesn't exist
        try {
            Path dir = Paths.get(pathLogDirectory);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created agent path tracking directory: {}", pathLogDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to create path tracking directory: {}", pathLogDirectory, e);
        }
    }
    
    /**
     * Record an agent's movement to a new cell.
     * Only records if this is an actual movement (different from last visited cell).
     * 
     * @param agentName Name of the agent
     * @param cellUri URI of the cell the agent is moving to
     */
    public void recordMovement(String agentName, String cellUri) {
        if (agentName == null || agentName.trim().isEmpty()) {
            return;
        }
        
        String lastCell = lastVisitedCell.get(agentName);
        
        // Only record if this is a different cell (actual movement)
        if (cellUri.equals(lastCell)) {
            log.debug("Agent {} re-requested current cell {}, not tracking", agentName, cellUri);
            return;
        }
        
        // Update last visited cell
        lastVisitedCell.put(agentName, cellUri);
        
        // Write to log file (will clear old file if it exists)
        writeMovementToLog(agentName, cellUri);
    }
    
    /**
     * Write a movement entry to the agent's path log file.
     * Clears old log file if it exists on first write (per server session).
     */
    private void writeMovementToLog(String agentName, String cellUri) {
        String sanitizedAgentName = agentName.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path logFile = Paths.get(pathLogDirectory, sanitizedAgentName + "-path.log");
        
        // If log file exists and hasn't been cleared yet in this session, delete it
        if (!clearedLogFiles.contains(sanitizedAgentName)) {
            try {
                if (Files.exists(logFile)) {
                    Files.delete(logFile);
                    log.info("Deleted existing log file for agent {} to start fresh session", agentName);
                }
            } catch (IOException e) {
                log.error("Failed to delete existing log file for agent {}", agentName, e);
            }
            // Mark as cleared for this session
            clearedLogFiles.add(sanitizedAgentName);
        }
        
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
        String logEntry = String.format("%s, %s%n", cellUri, timestamp);
        
        String logFileName = logFile.toString();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName, true))) {
            writer.write(logEntry);
            log.debug("Recorded movement for agent {}: {}", agentName, cellUri);
        } catch (IOException e) {
            log.error("Failed to write movement to log for agent {}: {}", agentName, cellUri, e);
        }
    }
    
    /**
     * Clear the tracking history for a specific agent.
     * Useful for resetting an agent's path or starting a new session.
     * 
     * @param agentName Name of the agent to reset
     */
    public void resetAgent(String agentName) {
        lastVisitedCell.remove(agentName);
        log.info("Reset path tracking for agent {}", agentName);
    }
    
    /**
     * Get the path log file name for an agent.
     * 
     * @param agentName Name of the agent
     * @return Path to the agent's log file
     */
    public String getLogFileName(String agentName) {
        String sanitizedAgentName = agentName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return String.format("%s/%s-path.log", pathLogDirectory, sanitizedAgentName);
    }
}
