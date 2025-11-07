Dev
```shell script
gradle runBold
```

Sim
```bash
./setup-MidMaze.sh
```

Docker Sim
```shell script
docker run -p 8080:8080 -e TASKNAME=setup-MidMaze -it bold-server
```

## Access Control

The server enforces location-based access control for agents navigating the maze:

- Agents identify themselves via the `Authorization` header (e.g., `Authorization: Agent mybot` or `Authorization: mybot`)
- Agents must start at the entrance (defined by `xhv:start` in `/maze`)
- GET requests are only allowed for cells reachable from the agent's current position
- Locked doors are enforced dynamically based on the RDF graph state
- Not providing the header allows to query any resource wihtout restrictions

## Path Tracking

Agent movements are automatically tracked and logged:

- Each agent gets a separate log file: `agent-paths/{agentname}-path.log`
- Format: `cell_uri, timestamp` (one line per movement)
- Only actual movements are tracked (re-requests of the same cell are ignored)
- Denied access attempts are not logged
- Useful for visualization and analysis of agent navigation strategies
