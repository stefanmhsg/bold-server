# EMAS Workshop Session Guide: MASE

This guide supports the live workshop session. It gives participants the commands and URLs to follow along, and gives the presenter compact evidence points for explaining MASE during the run.

## 1. MASE At A Glance

MASE is a Linked Data Multi-Agent Systems Environment for controlled experiments with hypermedia agents. The environment is exposed as read-write Linked Data: agents dereference RDF resources with HTTP `GET`, request environment changes with HTTP `POST`, and the server applies embodiment constraints plus SPARQL Update rules before the viewer shows the resulting environment dynamics.

| MASE term | Workshop explainer | Concrete form |
| --- | --- | --- |
| Hypermedia agent | An agent that discovers possible actions from Web resources instead of a fixed local API | HTTP client using `GET`, `POST`, Turtle, and links |
| Read-write Linked Data environment | The maze is not only visual; it is an HTTP-addressable RDF environment | `/maze`, `/cells/...`, `/agents/...` |
| RDF named graph | Each resource has a scoped RDF graph that can be inspected and updated | A cell URI names the graph for that cell |
| Embodiment constraint | The agent's current location constrains perception and action | `Authorization: <agent-name>` activates location-aware checks |
| Affordance / signifier | RDF tells the agent what actions are available or suggested | Direction predicates, locks, keys, `maze:green` |
| Environment dynamics | Requests can trigger state changes beyond the posted triples | SPARQL Update rules for movement, unlocks, UI, and traces |
| Observability | Each action can be connected to evidence | Request transcript, Cell Updates, Agent Movements, canvas |

| Component | Role in the session |
| --- | --- |
| Server | Linked Data interface, access validation, transaction handling, SPARQL rule execution |
| Scenario package | RDF data, rule files, runtime properties, optional agent sources and notes |
| Viewer | Live canvas, cell/agent inspectors, Cell Updates, Agent Movements, reset/export controls |
| Demo agent | Presenter-controlled HTTP client that executes one real request per `Next Request` |

## 2. Start The Starter Scenario

Use Docker first. It starts the SmallMaze server and viewer from [docker-compose.starter.yml](docker-compose.starter.yml).

| Service | URL |
| --- | --- |
| Viewer | `http://localhost:3000` |
| Server maze resource | `http://localhost:8080/maze` |

| Shell | Command |
| --- | --- |
| PowerShell | `docker compose -f docker-compose.starter.yml up --build` |
| Bash | `docker compose -f docker-compose.starter.yml up --build` |

Fallback local run:

| Task | PowerShell | Bash |
| --- | --- | --- |
| Server | `cd .\mase-server`<br>`.\gradlew.bat runMase --args="--scenario scenarios/smallmaze"` | `cd ./mase-server`<br>`./gradlew runMase --args="--scenario scenarios/smallmaze"` |
| Viewer | `cd .\mase-viewer`<br>`npm install`<br>`npm run dev` | `cd ./mase-viewer`<br>`npm install`<br>`npm run dev` |

| Startup evidence | Where to look |
| --- | --- |
| Viewer loads | Browser at `http://localhost:3000` for Docker, usually `http://localhost:5173` for local Vite |
| Viewer is connected | Connection indicator in the viewer |
| Maze is loaded | Canvas shows the SmallMaze layout |
| Server responds | Browser or HTTP client can load `http://localhost:8080/maze` |

## 3. Read The Viewer Layout

Orient the room before any agent action.

| Viewer area | What to show | Point to make |
| --- | --- | --- |
| Canvas | Cells, walls, arrows, locks, keys, and agent markers | The visual maze is derived from RDF state |
| Live Events controls | Reset Store, log controls, Demo Agent button | The session can be reset and stepped without editing code |
| Agent Movements | One row per movement event | Movement is observable as an event, not only as animation |
| Cell Updates | Transaction rows from startup, reset, and POST requests | State changes are attributable to requests and rules |
| Cell inspector | Double-click a cell | A maze cell is an RDF named graph |
| Agent inspector | Double-click an agent movement row or marker when present | Agent identity and location are server-side state |

## 4. Step Through SmallMaze With The Demo Agent

Open the inline demo panel from the viewer and use it as a request boundary marker. Click `Next Request` until the relevant moment appears; do not narrate every click.

| Control | Value |
| --- | --- |
| Button | `Demo Agent` |
| Agent name | `demo-agent` *name has to be unique in mase |
| Prefer green signifiers | Enabled |
| Reset action | `Reset Demo Agent` |
| Step action | `Next Request` |

| Moment to catch | What appears | Point to make |
| --- | --- | --- |
| Session reset | Step `0`, ready status, no transcript | Demo Agent reset clears only presenter-control state |
| Maze discovery | `GET .../maze`, Turtle response, start cell in decision text | The agent discovers the environment through the Linked Data interface |
| First entry | `POST` body with `dynmaze:entersFrom </maze>` | Initial embodiment is established by a request, not by the viewer |
| Cell perception | `GET .../cells/...`, Turtle response with directions, locks, or keys | Perception is local once the agent is embodied |
| Signifier use | Decision mentions `maze:green` when present | A signifier can guide action selection without hardcoding the route |
| DFS fallback | Decision mentions DFS or backtracking | The agent still works when no signifier is available |
| Movement | `POST` body with `dynmaze:entersFrom <source-cell>` | Movement is a requested state change checked against adjacency |
| Unlock | `POST` body with `dynmaze:keyValue` | Action effects are scenario rules over RDF state |
| Exit | Complete status or movement to `/cells/999` | The same request/rule/event chain covers the whole run |

| Request detail | Why it matters |
| --- | --- |
| Method | Separates perception (`GET`) from action (`POST`) |
| URL | Shows which RDF resource is being read or changed |
| `Authorization: demo-agent` | Carries agent identity and activates embodiment checks |
| Turtle body | Encodes the requested action as RDF |
| Response status | Shows whether the server accepted or rejected the request |
| Response body | Shows the RDF representation returned to the agent |

## 5. Inspect Cell Updates

Use this immediately after a successful `POST` from the demo agent.

| Number | Action | Evidence |
| --- | --- | --- |
| 1 | Open the Cell Updates table | A transaction row appears for the POST |
| 2 | Match the row to the demo panel | Agent, graph, and timing correspond to the last request |
| 3 | Expand the row | Request body and RDF diffs are visible when trace mode is `full` |
| 4 | Compare canvas and row | The visual movement or unlock corresponds to changed RDF |

SmallMaze currently uses full transaction traces in [scenario.properties](mase-server/scenarios/smallmaze/scenario.properties).

| Trace mode | Use | Cell Updates detail |
| --- | --- | --- |
| `off` | Quiet runs | No transaction debug events |
| `summary` | Longer runs | Transaction header, status, graph, agent, rule count |
| `full` | Teaching one request | Request body, merge triples, per-rule RDF diffs |

## 6. Explain SPARQL Rules

Rules define environment dynamics: they materialize the effects that should follow from accepted HTTP requests.

| Effect | Rule file | What to point at |
| --- | --- | --- |
| Agent enters from `/maze` | [move_start.rq](mase-server/scenarios/smallmaze/rules/global/move_start.rq) | First valid entry creates visible agent location |
| Agent moves between cells | [move.rq](mase-server/scenarios/smallmaze/rules/global/move.rq) | Movement updates containment and event state |
| Red lock opens | [unlock-redkey.rq](mase-server/scenarios/smallmaze/rules/scenario/unlock-redkey.rq) | A posted `dynmaze:keyValue` changes lock state and restores a route |
| Green lock opens | [unlock-greenkey.rq](mase-server/scenarios/smallmaze/rules/scenario/unlock-greenkey.rq) | Same pattern with a different key type |
| Viewer arrows and markers | [ui.rq](mase-server/scenarios/smallmaze/rules/global/ui.rq) | UI is generated from RDF, not hardcoded in the viewer |
| Footstep/traffic traces | [ui-stigmergy-footsteps.rq](mase-server/scenarios/smallmaze/rules/global/ui-stigmergy-footsteps.rq) | Stigmergy can be represented as RDF state |

| Rule concept | Meaning in MASE |
| --- | --- |
| `DELETE` / `INSERT` | Defines what triples change |
| `WHERE` | Selects the RDF pattern that enables the effect |
| Named graph | Keeps changes scoped to the affected resource |
| Rule order | Configured in [scenario.properties](mase-server/scenarios/smallmaze/scenario.properties) |
| Transaction | Request body merge and rule execution happen as one server-side operation |

## 7. Reset The Store

Use this before repeating the demo or before showing access-control behavior.

| Number | Action | Result |
| --- | --- | --- |
| 1 | Click `Reset Store` in the viewer | Reset dialog opens |
| 2 | Choose `Discard logs` for rehearsal or export logs when preserving evidence | Viewer handles the current event archive |
| 3 | Confirm reset | Server reloads SmallMaze data and rules |
| 4 | Click `Reset Demo Agent` | Demo session restarts from step `0` |

| Reset type | What it clears | What it does not clear |
| --- | --- | --- |
| Reset Store | RDF scenario state, live event tables, archive state after confirmation | Running Docker containers |
| Reset Demo Agent | In-memory demo-agent path, current step, last transcript | RDF store and existing viewer logs |

## 8. Embodiment And Access Control

The `Authorization` header identifies the agent whose embodied state should constrain the request.

| Request style | Meaning |
| --- | --- |
| No `Authorization` header | Unrestricted debug inspection |
| `Authorization: demo-agent` | Requests are checked against `demo-agent` location |
| `Authorization: bob` | Requests are checked against `bob` location |

Use these after the demo agent has entered the start cell.

| Case | PowerShell | Bash | Expected result |
| --- | --- | --- | --- |
| Debug read of `/maze` | `curl.exe -H "Accept: text/turtle" http://localhost:8080/maze` | `curl -H "Accept: text/turtle" http://localhost:8080/maze` | Succeeds without embodiment checks |
| Embodied read of current/global resource | `curl.exe -H "Accept: text/turtle" -H "Authorization: demo-agent" http://localhost:8080/maze` | `curl -H "Accept: text/turtle" -H "Authorization: demo-agent" http://localhost:8080/maze` | Succeeds for the global maze resource |
| One failure example | `curl.exe -i -H "Accept: text/plain" -H "Authorization: demo-agent" http://localhost:8080/cells/999` | `curl -i -H "Accept: text/plain" -H "Authorization: demo-agent" http://localhost:8080/cells/999` | `HTTP/1.1 403` with an access-denied message unless the agent is at the exit |

| Rule enforced by Java | Workshop wording |
| --- | --- |
| First entry must come from `/maze` to the start cell | An agent cannot appear anywhere it wants |
| GET is local after entry | An embodied agent perceives its current cell |
| POST is local except movement to an adjacent target | An embodied agent acts from its current position |
| Movement uses `dynmaze:entersFrom` | The body declares where the agent claims to move from |
| Invalid identity or invalid source cell is rejected | The server, not the viewer, enforces embodiment |

## 9. Specify Action Effects

An action effect starts with an RDF request body and becomes environment state through SPARQL Update rules.

| Action | Request body pattern | Rule effect |
| --- | --- | --- |
| Enter maze | `<agent> dynmaze:entersFrom </maze> .` | Adds the agent to the start cell |
| Move | `<agent> dynmaze:entersFrom <source-cell> .` | Moves the agent from source to target |
| Unlock | `<lock-cell> dynmaze:keyValue "redkey" .` | Changes lock state and restores an RDF route |

| Where to customize | File or folder |
| --- | --- |
| Initial RDF world | [SmallMaze.trig](mase-server/scenarios/smallmaze/data/SmallMaze.trig) |
| Runtime properties | [scenario.properties](mase-server/scenarios/smallmaze/scenario.properties) |
| Global behavior rules | [rules/global](mase-server/scenarios/smallmaze/rules/global) |
| Scenario-specific behavior rules | [rules/scenario](mase-server/scenarios/smallmaze/rules/scenario) |
| Disabled or experimental rules | `rules-disabled` folder inside a scenario |
| Optional scenario agent code | [SmallMaze agents README.md](mase-server/scenarios/smallmaze/agents/README.md) |

## 10. Showcase The Workshop CCRS Scenario

Switch from the starter scenario to the dedicated EMAS workshop scenario package: [emas-workshop README.md](mase-server/scenarios/emas-workshop/README.md). This package is copied from CCRS and defaults to [CcrsMazeV2.trig](mase-server/scenarios/emas-workshop/data/CcrsMazeV2.trig) so the live showcase can run without depending on A2A keyholder timing.

| Step | PowerShell | Bash |
| --- | --- | --- |
| Stop starter stack | `docker compose -f docker-compose.starter.yml down` | `docker compose -f docker-compose.starter.yml down` |
| Start workshop CCRS server/viewer | `docker compose -f docker-compose.emas-workshop.yml --profile viewer up --build -d mase-viewer` | `docker compose -f docker-compose.emas-workshop.yml --profile viewer up --build -d mase-viewer` |
| Open viewer | `http://localhost:3000` | `http://localhost:3000` |
| Start CCRS agents | `docker compose -f docker-compose.emas-workshop.yml up --build -d ccrs-agent` | `docker compose -f docker-compose.emas-workshop.yml up --build -d ccrs-agent` |
| Optional A2A/keyholder service | `docker compose -f docker-compose.emas-workshop.yml up -d --no-build keyholder-agent` | `docker compose -f docker-compose.emas-workshop.yml up -d --no-build keyholder-agent` |

| Showcase feature | What to show | Customization hook |
| --- | --- | --- |
| Larger scenario topology | CCRS maze canvas has multiple zones and disconnected components | Dataset and maze type in [CcrsMazeV2.trig](mase-server/scenarios/emas-workshop/data/CcrsMazeV2.trig) |
| Signifiers | Green route hints generated from CCRS rules | [ccrs.rq](mase-server/scenarios/emas-workshop/rules/scenario/ccrs.rq) |
| Locks and keys | Agents encounter locked cells and key values | [unlock-keys.rq](mase-server/scenarios/emas-workshop/rules/scenario/unlock-keys.rq) |
| Stigmergy | Repeated movement creates visible traces or traffic evidence | [ui-stigmergy-footsteps.rq](mase-server/scenarios/emas-workshop/rules/global/ui-stigmergy-footsteps.rq) |
| Viewer materialization | RDF is turned into arrows, colors, and markers | [ui.rq](mase-server/scenarios/emas-workshop/rules/global/ui.rq) |
| Scenario launch defaults | Agent timing and trace settings are scenario-owned | [scenario.properties](mase-server/scenarios/emas-workshop/scenario.properties) |

## 11. Customize A MASE Scenario

Treat a scenario folder as the unit of customization. SmallMaze is the minimal teaching package; EMAS workshop CCRS is the richer showcase package.

| Customization goal | SmallMaze file | EMAS workshop CCRS file |
| --- | --- | --- |
| Change the maze topology | [SmallMaze.trig](mase-server/scenarios/smallmaze/data/SmallMaze.trig) | [CcrsMazeV2.trig](mase-server/scenarios/emas-workshop/data/CcrsMazeV2.trig) |
| Change runtime behavior | [scenario.properties](mase-server/scenarios/smallmaze/scenario.properties) | [scenario.properties](mase-server/scenarios/emas-workshop/scenario.properties) |
| Change movement or startup effects | [rules/global](mase-server/scenarios/smallmaze/rules/global) | [rules/global](mase-server/scenarios/emas-workshop/rules/global) |
| Add scenario-specific effects | [rules/scenario](mase-server/scenarios/smallmaze/rules/scenario) | [rules/scenario](mase-server/scenarios/emas-workshop/rules/scenario) |
| Keep experimental rules out of the run | `rules-disabled` folder inside the scenario | `rules-disabled` folder inside the scenario |
| Add or document agents | [SmallMaze agents README.md](mase-server/scenarios/smallmaze/agents/README.md) | [EMAS workshop agents README.md](mase-server/scenarios/emas-workshop/agents/README.md) |

Optional follow-up: [mase-creator README.md](mase-creator/README.md) can be used to generate or adapt MASE scenarios, but it is not part of the main live path.

## 12. Optional Local Agent Runs

Use this only after the controlled demo and Docker showcase are clear.

| Agent | PowerShell | Bash | Use |
| --- | --- | --- | --- |
| SmallMaze sample DFS `bob` | `cd .\mase-server`<br>`.\gradlew.bat runSmallMazeBobAgent` | `cd ./mase-server`<br>`./gradlew runSmallMazeBobAgent` | Let a normal scenario agent run without presenter stepping |
| EMAS workshop CCRS agents | `cd .\mase-server`<br>`.\gradlew.bat -PmaseScenario=scenarios/emas-workshop runCcrsAgent` | `cd ./mase-server`<br>`./gradlew -PmaseScenario=scenarios/emas-workshop runCcrsAgent` | Run the larger scenario agents against a local server |
| Optional A2A/keyholder | `cd .\mase-server`<br>`.\gradlew.bat -PmaseScenario=scenarios/emas-workshop runKeyHolderAgent` | `cd ./mase-server`<br>`./gradlew -PmaseScenario=scenarios/emas-workshop runKeyHolderAgent` | Show the keyholder service separately |

## 13. Recovery

| Symptom | Fast check | Recovery |
| --- | --- | --- |
| Viewer does not load | `http://localhost:3000` | Restart the active Docker Compose stack |
| Viewer is disconnected | Server URL and WebSocket status | Restart server, then refresh viewer |
| Ports are already in use | `8080`, `3000`, or `8095` is occupied | Stop the previous Compose stack before starting the next one |
| Maze is stale | Old markers or old Cell Updates rows | Use Reset Store, then Reset Demo Agent |
| Demo Agent is stuck | Demo panel status is `error` | Reset Demo Agent |
| No request bodies in Cell Updates | Trace mode | Use `mase.transaction.trace = full`, then restart server |
