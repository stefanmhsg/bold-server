# MASE Viewer

## Getting Started

Install dependencies with `npm install` (or `pnpm install` or `yarn`).

#### Start a development server:

```sh
npm run dev

# or start the server and open the app in a new browser tab
npm run dev -- --open
```

#### Docker
```sh
# build the viewer
docker build . -t mase-viewer
# run the viewer
docker run -p 3000:3000 -it mase-viewer
```
The app will be running at http://127.0.1.1:3000/ or http://localhost:3000 (or the port you specified).

## MASE Server Configuration

By default the viewer targets `http://localhost:8080` and derives the WebSocket URL as `ws://localhost:8080/ws`.

For local `npm run dev`, set these only when the server is not on the default port:

```sh
PUBLIC_MASE_SERVER_HTTP_URL=http://localhost:8080
PUBLIC_MASE_SERVER_WS_URL=ws://localhost:8080/ws
```

For Docker, the SvelteKit server may need an internal container URL while the browser still needs a host-reachable public URL:

```sh
docker run -p 3000:3000 \
  -e MASE_SERVER_INTERNAL_HTTP_URL=http://mase-server:8080 \
  -e PUBLIC_MASE_SERVER_HTTP_URL=http://localhost:8080 \
  -e PUBLIC_MASE_SERVER_WS_URL=ws://localhost:8080/ws \
  mase-viewer
```

`MASE_SERVER_INTERNAL_HTTP_URL` is used by the viewer server process to fetch `/admin/maze`. `PUBLIC_MASE_SERVER_HTTP_URL` and `PUBLIC_MASE_SERVER_WS_URL` are returned to the browser for reset requests and live WebSocket events.



## What you can do in MASE Viewer

- View the live maze layout and watch updates as events arrive from the server.
- Open the Cell Inspector (double-click a cell) to inspect that cell’s RDF graph in Turtle.
- Open the Agent Inspector (double-click an agent in the Agent Movements table) to inspect the agent graph.
- Post Turtle triples from the Agent Inspector directly to the selected agent graph.
- Monitor Agent Movements in real time (time, agent, and location).
- Monitor Transaction/Cell Updates in real time when server transaction tracing is enabled (transaction id, time, trigger, agent, graph, status, and executed rule count).
- Double-click a Transaction/Cell Updates row to expand details when trace events are available. Summary mode shows transaction id, timing, status, graph, agent, and rule count. Full mode also shows request body, merge added/removed triples, and per-rule added/removed triples.
- Export archived logs as NDJSON, with event-type checkboxes for Agent Movements, Cell Updates, UI Upserts, and UI Deletes.
- Reset the server store after choosing whether to export selected log event types, discard logs, or cancel.
- Save exports to a browser-selected file. For a repository-local target, choose `mase-viewer/log` in the save dialog once; supported browsers remember that location for later exports.
- Read triples in grouped context form for clarity:
	- `<context = graph_uri> :`
	- `<s> <p> <o>`
- Resize both event tables vertically (up/down) to see more or fewer rows while keeping layout width fixed.

## Live Updates and Transaction Traces

The viewer receives WebSocket events from the MASE server.

Runtime rendering does not require full transaction traces. The canvas is kept in sync from committed runtime events:

- `AGENT_MOVED` updates agent marker positions.
- `UI_UPSERT` creates or updates RDF-defined visual elements.
- `UI_DELETE` removes RDF-defined visual elements.

`TRANSACTION` events are controlled by the selected server scenario properties file:

```properties
mase.transaction.trace = summary
```

- `summary` emits lightweight headers: transaction id, time, trigger, agent, graph, status, error, and executed rule count.
- `full` also emits request bodies, merged triples, and per-rule RDF diffs.
- `off` disables the Transaction/Cell Updates stream. The canvas still updates from `AGENT_MOVED`, `UI_UPSERT`, and `UI_DELETE`.

## Background Coloring and Optimal Route

- Cell background coloring is driven by RDF UI updates: use `ui:layer "cellBackground"` together with `ui:fill`.
- Optimal route coloring is frontend-only (visual aid only) and can be toggled in the UI.
- The route set is selected by scenario, therefore the admin snapshot must provide `scenarioName` in the loaded data.
- If `scenarioName` is missing or has no configured route mapping, no optimal-route overlay is applied.

<img src="Maze Viewer v9.png" width="800">
