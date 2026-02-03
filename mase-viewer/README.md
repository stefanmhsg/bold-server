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


- Double click on a cell to open the cell inspector and view the current state of the RDF graph.