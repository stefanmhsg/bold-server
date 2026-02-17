# Maze Viewer

## Getting Started

Install dependencies with `npm install` (or `pnpm install` or `yarn`).

Start a development server:

```sh
npm run dev

# or start the server and open the app in a new browser tab
npm run dev -- --open
```

docker
```sh
# build the viewer
docker build . -t mase-viewer
# run the viewer
docker run -p 3000:3000 -it mase-viewer
```
The app will be running at http://127.0.1.1:3000/ or http://localhost:3000 (or the port you specified).


<img src="Maze Viewer v2.png" width="800">


## What you can do in MASE Viewer

- View the live maze layout and watch updates as events arrive from the server.
- Open the Cell Inspector (double-click a cell) to inspect that cell’s RDF graph in Turtle.
- Open the Agent Inspector (double-click an agent in the Agent Movements table) to inspect the agent graph.
- Post Turtle triples from the Agent Inspector directly to the selected agent graph.
- Monitor Agent Movements in real time (time, agent, and location).
- Monitor Transaction/Cell Updates in real time (trigger, agent, graph, and rules changed summary).
- Double-click a Transaction/Cell Updates row to expand full details:
	- request body,
	- merge added/removed triples,
	- per-rule added/removed triples,
	- status/error and timing.
- Read triples in grouped context form for clarity:
	- `<context = graph_uri> :`
	- `<s> <p> <o>`
- Resize both event tables vertically (up/down) to see more or fewer rows while keeping layout width fixed.