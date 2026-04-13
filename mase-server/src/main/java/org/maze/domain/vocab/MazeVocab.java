package org.maze.domain.vocab;

import java.util.Set;

/**
 * RDF vocabulary constants for the Maze simulation domain.
 * Centralizes all namespace URIs and predicates used throughout the application.
 */
public final class MazeVocab {
    
    // Prevent instantiation
    private MazeVocab() {
        throw new AssertionError("Utility class - do not instantiate");
    }
    
    // ============ Namespaces ============
    
    /**
     * Maze vocabulary namespace.
     * Used for maze-specific concepts like cells, doors, keys.
     */
    public static final String MAZE_NS = "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#";
    
    /**
     * XHTML vocabulary namespace.
     * Used for navigation predicates like 'next', 'start'.
     */
    public static final String XHV_NS = "http://www.w3.org/1999/xhtml/vocab#";

    /**
     * XML Schema Definition namespace.
     * Used for data types like dateTime, integer, etc.
     */
    public static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    
    /**
     * Dynamic Maze vocabulary namespace.
     * Used for dynamic cell states and properties.
     */
    public static final String DYNMAZE_NS = "https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#";
    
    /**
     * Stigmergy vocabulary namespace.
     * Used for agent communication via environment markers.
     */
    public static final String STIGMERGY_NS = "https://example.org/stigmark#"; //TODO

    /**
     * UI vocabulary namespace.
     * Used for UI element properties and types.
     */
    public static final String UI_NS = "https://example.org/ui#"; //TODO
    
    // ============ Common Predicates ============
    
    /**
     * Predicate for maze navigation - request by Agent to move to target cell.
     */
    public static final String ENTERS_FROM = DYNMAZE_NS + "entersFrom";

    /**
     * Object for successful movement - indicates agent has moved to target cell.
     */
    public static final String MOVE_SUCCESS_EVENT = DYNMAZE_NS + "moveSuccessEvent";
    
    /**
     * Predicate for identifying agents in the maze.
     */
    public static final String AGENT = DYNMAZE_NS + "agent";

    /**
     * Predicate for target cell in movement events.
     */
    public static final String TARGET_CELL = DYNMAZE_NS + "targetCell";

    /**
     * Predicate for timestamping events.
     */
    public static final String TIMESTAMP = XSD + "dateTime";

    /**
     * Predicate for maze entrance - identifies the starting cell.
     */
    public static final String START = XHV_NS + "start";
    
    /**
     * Predicate for agent location - links agent to current cell.
     */
    public static final String AT = MAZE_NS + "at";
    
    /**
     * Predicate for cell status (locked, unlocked, etc.).
     */
    public static final String HAS_STATUS = DYNMAZE_NS + "hasStatus";
    
    /**
     * Predicate for cell state.
     */
    public static final String STATE = DYNMAZE_NS + "state";
    
    /**
     * Predicate for quantitative stigmergy markers.
     */
    public static final String QUANTITATIVE = STIGMERGY_NS + "quantitative";
    
    // ============ UI Vocabulary ============
    /**
     * Predicate for UI element Konva-type (e.g., Rect, Circle).
     */
    public static final String UI_TYPE = UI_NS + "konvaType";

    /**
     * Predicate for UI element render layer.
     */
    public static final String UI_LAYER = UI_NS + "layer";
    
    /**
     * Predicate for UI element direction (e.g., N, E, S, W).
     */
    public static final String UI_DIRECTION = UI_NS + "direction";

    /**
     * Predicate for UI element positional anchor:
     * NW  N  NE
     * W   C   E
     * SW  S  SE
     */
    public static final String UI_ANCHOR   = UI_NS + "anchor";
    
    /**
     * Predicate for UI element X offset from anchor.
     */
    public static final String UI_OFFSET_X = UI_NS + "offsetX";
    
    /**
     * Predicate for UI element Y offset from anchor.
     */
    public static final String UI_OFFSET_Y = UI_NS + "offsetY";

    /**
     * Predicate linking a cell to a UI element.
     */
    public static final String UI_HAS_UI_ELEMENT = UI_NS + "hasUiElement";

    // ============ State Management ============
    
    /**
     * Predicates that represent state in the maze.
     * When these predicates are updated by rules, old values are replaced (not accumulated).
     * This ensures state consistency - e.g., a cell can't be both locked and unlocked.
     */
    @Deprecated // TODO
    public static final Set<String> STATE_PREDICATES = Set.of(
        HAS_STATUS,
        STATE,
        QUANTITATIVE
    );
    
    // ============ Utility Methods ============
    
    /**
     * Check if a predicate represents state (should replace old values).
     * 
     * @param predicateUri the predicate URI to check
     * @return true if this is a state predicate
     */
    @Deprecated // TODO
    public static boolean isStatePredicate(String predicateUri) {
        return STATE_PREDICATES.contains(predicateUri);
    }
    
    /**
     * Build a full IRI from a namespace and local name.
     * 
     * @param namespace the namespace URI
     * @param localName the local name
     * @return the full IRI
     */
    public static String iri(String namespace, String localName) {
        return namespace + localName;
    }
}
