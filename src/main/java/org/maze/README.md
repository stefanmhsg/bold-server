# Maze Game Engine Package

This package (`org.maze`) contains the game engine logic for the maze navigation system in the BOLD server. It has been extracted from the linked data dereferencing resource to provide a clean separation of concerns between REST API handling and game rules.

## Architecture

The maze game engine follows a modular design with clear responsibilities:

```
┌─────────────────────────────────────────────────────────┐
│         LinkedDataDereferenceResource (REST API)        │
└───────────────────────┬─────────────────────────────────┘
                        │ uses
                        ▼
┌─────────────────────────────────────────────────────────┐
│              MazeGameEngine (Orchestrator)              │
│  - Coordinates all game rules and agent tracking        │
│  - Main entry point for access validation               │
│  - Executes dynamic rules after state changes           │
└──┬────────────┬────────────┬──────────────┬─────────┬───┘
   │            │            │              │         │
   │ uses       │ uses       │ uses         │ uses    │ uses
   ▼            ▼            ▼              ▼         ▼
┌──────────┐ ┌──────────┐ ┌──────────┐  ┌──────────────┐ ┌──────────┐
│  Access  │ │ Location │ │   Path   │  │   Request    │ │   Rule   │
│ Control  │ │ Tracker  │ │ Tracker  │  │   Tracker    │ │  Engine  │
└──────────┘ └──────────┘ └──────────┘  └──────────────┘ └─────┬────┘
                                                                  │ uses
                                                                  ▼
                                                          ┌───────────────┐
                                                          │  Rule Loader  │
                                                          │  (SPARQL .rq) │
                                                          └───────────────┘
```

## Components

### 1. **MazeGameEngine** (Main Orchestrator)
- **Purpose**: Central coordinator for all maze game logic
- **Responsibilities**:
  - Validates agent access to cells
  - Coordinates between access control, location tracking, and logging
  - Provides a simple API for the REST layer
- **Key Method**: `validateAccess(agentName, cellUri, method)` → `AccessResult`

### 2. **MazeAccessControl** (Rules Engine)
- **Purpose**: Enforces maze navigation rules using RDF/SPARQL queries
- **Responsibilities**:
  - Checks if cells are accessible based on maze graph connections
  - Validates entrance cell (xhv:start predicate)
  - Queries RDF graph for valid directions (north, south, east, west, exit)
- **Key Methods**:
  - `isAccessAllowed(currentCell, requestedCell)` → boolean
  - `isEntranceCell(cellUri)` → boolean

### 3. **AgentLocationTracker**
- **Purpose**: Tracks current location of each agent
- **Responsibilities**:
  - Maintains in-memory map of agent positions
  - Thread-safe using ConcurrentHashMap
  - Provides location updates and queries
- **Key Methods**:
  - `getLocation(agentName)` → cellUri or null
  - `updateLocation(agentName, cellUri)`
  - `removeAgent(agentName)`

### 4. **MazePathTracker**
- **Purpose**: Records agent movement history to log files
- **Responsibilities**:
  - Logs only actual movements (not re-requests)
  - Creates one log file per agent: `{agentName}-path.log`
  - Format: `cellUri, timestamp`
  - Clears old logs on server restart
- **Log Directory**: `agent-paths/`

### 5. **MazeRequestTracker**
- **Purpose**: Detailed audit logging of all requests
- **Responsibilities**:
  - Logs ALL requests including denied ones and re-requests
  - Tracks request repetition counts
  - Creates one log file per agent: `{agentName}-requests.log`
  - Format: `cellUri, operation, count, allowed/denied, timestamp`
- **Log Directory**: `agent-requests/`

### 6. **AgentAuthUtil**
- **Purpose**: Utility for extracting agent identity from HTTP headers
- **Supported Formats**:
  - `Authorization: Agent myagent`
  - `Authorization: myagent`
- **Key Method**: `extractAgentName(authorization)` → agentName or null

### 7. **MazeRuleEngine** (Dynamic Rules)
- **Purpose**: Executes SPARQL-based rules to modify maze topology dynamically
- **Responsibilities**:
  - Loads .rq rule files from resources directory
  - Executes CONSTRUCT queries after state changes
  - Adds generated triples to RDF graph (unlocking doors, toggling switches)
- **Trigger**: Called automatically after every POST request
- **Package**: `org.maze.domain.rules`

### 8. **MazeRuleLoader** (Rule Discovery)
- **Purpose**: Discovers and loads SPARQL rule files from resources
- **Features**:
  - Maze-specific subdirectory support (UnsafeMaze/, BigMaze/, etc.)
  - Generic rule fallback for unknown mazes
  - Configurable via task name in Configurator
- **Rule Location**: `src/main/resources/rules/`
- **See**: `resources/rules/README.md` for detailed rule documentation

## Game Rules

### Static Access Control Rules

These rules are enforced on every GET/POST request:

1. **Entrance Rule**: First access must be to the entrance cell (defined by `xhv:start` in `/maze` graph)
2. **Adjacent Cell Rule**: Can only move to cells connected via maze predicates:
   - `maze:north`, `maze:south`, `maze:east`, `maze:west`, `maze:exit`
3. **Graph-Based Navigation**: Connections must exist in the RDF graph (locked doors are not present)
4. **Non-Cell Access**: Resources outside `/cells/` are always accessible (e.g., `/maze`, `/map`)

### Dynamic Rule Execution

Dynamic rules modify the maze topology based on game state:

1. **Unlock Rules**: When agent finds a key, doors unlock (new connections added to graph)
2. **Switch Rules**: When agent flips a switch, passages open/close (connections toggled)
3. **Rule Trigger**: Rules execute automatically after every POST request
4. **Rule Format**: SPARQL CONSTRUCT queries in `.rq` files
5. **Maze-Specific**: Different rules for UnsafeMaze (5 rules), BigMaze (7 rules), etc.

**Example Rule Flow**:
```
Agent POST /cells/5 → Pick up red key → dyn:foundAt dyn:RedKey added to graph
→ MazeGameEngine.executeRules() called
→ unlock-redkey.rq WHERE clause matches
→ CONSTRUCT adds: <cells/0> maze:east <cells/7>
→ Door unlocked! Agent can now move from cell 0 to cell 7
```

See `resources/rules/README.md` for detailed rule documentation.

### Access Flow
```
1. Agent requests cell X
2. Extract agent name from Authorization header
3. Check if cell X is a cell resource (/cells/)
   └─ No → Allow access (metadata/map resources)
   └─ Yes → Continue validation
4. Get agent's current location
   └─ No location → Check if X is entrance
      └─ Yes → Allow & record as starting position
      └─ No → Deny (must start at entrance)
   └─ Has location → Check if X is adjacent to current
      └─ Yes → Allow & update location
      └─ No → Deny (not accessible from current position)
5. Log request and movement (if allowed)
6. Return access result
```


## Future Enhancements

Potential improvements to consider:

1. **Persistent Agent State**: Store agent locations in RDF graph instead of memory
2. **Game Session Management**: Track game sessions with start/end times
3. **Multi-Maze Support**: Support multiple concurrent maze instances
4. **Admin API**: REST endpoints to query agent locations, reset games
5. **Metrics & Analytics**: Track maze completion times, path efficiency
6. **Rule Configuration**: Externalize game rules to configuration files
7. **Event System**: Publish events for agent movements, access denials
8. **Replay System**: Reconstruct agent paths from log files

## Related Documentation

- [RDF4J Documentation](https://rdf4j.org/documentation/)
- [Brick Schema](https://brickschema.org/)
- [Real Estate Core Ontology](https://doc.realestatecore.io/)
- [Maze Vocabulary](https://kaefer3000.github.io/2021-02-dagstuhl/vocab)
