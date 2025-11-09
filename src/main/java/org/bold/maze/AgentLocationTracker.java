package org.bold.maze;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the current location of each agent in the maze.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class AgentLocationTracker {
    
    // Track agent locations: agent name -> current cell URI
    private final Map<String, String> agentLocations = new ConcurrentHashMap<>();
    
    /**
     * Get the current location of an agent.
     * 
     * @param agentName the name of the agent
     * @return the URI of the agent's current cell, or null if not yet tracked
     */
    public String getLocation(String agentName) {
        return agentLocations.get(agentName);
    }
    
    /**
     * Update the location of an agent.
     * 
     * @param agentName the name of the agent
     * @param cellUri the URI of the new cell location
     */
    public void updateLocation(String agentName, String cellUri) {
        agentLocations.put(agentName, cellUri);
    }
    
    /**
     * Remove an agent from tracking (e.g., when they exit the maze).
     * 
     * @param agentName the name of the agent to remove
     */
    public void removeAgent(String agentName) {
        agentLocations.remove(agentName);
    }
    
    /**
     * Get all tracked agents and their locations.
     * 
     * @return a map of agent names to their current cell URIs
     */
    public Map<String, String> getAllLocations() {
        return new ConcurrentHashMap<>(agentLocations);
    }
    
    /**
     * Clear all agent locations (e.g., for resetting the game).
     */
    public void clear() {
        agentLocations.clear();
    }
}
