# CCRS Scenario

---

Please note that everything related to "CCRS" is tailored to my master's thesis and is thus not fully explained here.

Nevertheless, I invite you to run the `ccrs` scenario as a demonstration of what is possible with the MASE project.

---

Data: [CcrsMazeV1.trig](mase-server/scenarios/ccrs/data/CcrsMazeV1.trig) and [CcrsMazeV2.trig](mase-server/scenarios/ccrs/data/CcrsMazeV2.trig)

Infrastructure agents:

- [CcrsAgent.java](mase-server/scenarios/ccrs/agents/src/main/java/org/maze/scenarios/ccrs/agents/CcrsAgent.java)
- [KeyHolderAgent.java](mase-server/scenarios/ccrs/agents/src/main/java/org/maze/scenarios/ccrs/agents/KeyHolderAgent.java)

## Run with Docker

Run these commands from the repository root.

### 1. Start the Viewer

Start the Docker viewer first:

```shell
docker compose -f docker-compose.ccrs.yml --profile viewer up --build -d mase-viewer
```

Because [docker-compose.ccrs.yml](docker-compose.ccrs.yml) declares `mase-viewer` as depending on `mase-server`, this also starts the CCRS server. The viewer is available at http://localhost:3000 and the server is available at http://localhost:8080.

Keep the viewer running between scenario runs. It is the fastest way to reset the running server and inspect the next run.

### 2. Start the CCRS Agent Stack

Start the one-shot CCRS infrastructure agents:

```shell
docker compose -f docker-compose.ccrs.yml up --build -d ccrs-agent keyholder-agent
```

This uses the already running server and starts:

- `ccrs-agent` after a short server startup delay, with Docker-safe request timing
- `keyholder-agent` roughly one minute after the CCRS infrastructure agents start

The Keyholder A2A endpoint is exposed on http://127.0.0.1:8095.

### 3. Reset and Rerun Without Rebuilding

After an experiment-agent run, reset the scenario in the viewer. Use the viewer reset action and choose either to export or discard the current logs before confirming the reset.

The reset endpoint reloads the active CCRS scenario data and rules inside the running `mase-server` container. The viewer and server stay up.

Then recreate only the one-shot agent containers, without rebuilding the image:

```shell
docker compose -f docker-compose.ccrs.yml up -d --no-build --force-recreate ccrs-agent keyholder-agent
```

Use this loop for repeated CCRS runs:

1. Reset the scenario via the viewer.
2. Run `docker compose -f docker-compose.ccrs.yml up -d --no-build --force-recreate ccrs-agent keyholder-agent`.
3. Leave `mase-viewer` and `mase-server` running.

Rebuild only when Java code, Dockerfiles, dependencies, or the viewer build changed.

The startup delays can be adjusted with environment variables:

```shell
$env:CCRS_AGENT_START_DELAY = "15"
$env:KEYHOLDER_AGENT_START_DELAY = "75"
docker compose -f docker-compose.ccrs.yml up -d --no-build --force-recreate ccrs-agent keyholder-agent
```

The CCRS infrastructure agent keeps the Gradle defaults of 3 seconds between spawned agents and 10 seconds per HTTP request unless overridden. The Docker Compose CCRS file uses more conservative defaults because the server and agents share Docker Desktop resources:

```shell
$env:CCRS_AGENT_DISPATCH_INTERVAL_MS = "5000"
$env:CCRS_AGENT_REQUEST_TIMEOUT_SECONDS = "60"
docker compose -f docker-compose.ccrs.yml up -d --no-build --force-recreate ccrs-agent keyholder-agent
```

## Run with Gradle

1. Start the server:

```shell
cd .\mase-server\
gradle runMase --args="--scenario scenarios/ccrs"
```

Variants: in [scenario.properties](mase-server/scenarios/ccrs/scenario.properties) select the dataset version.

- `CcrsMazeV1.trig`: red key removed, use with the keyholder agent
- `CcrsMazeV2.trig`: red key is placed in the cell

2. Start the Frontend:

```shell
cd .\mase-viewer\
npm run dev
```

3. In another terminal, run the infrastructure agents:

```shell
cd .\mase-server\
gradle runCcrsAgent
```

4. In another terminal, run the Keyholder A2A agent:

```shell
cd .\mase-server\
gradle runKeyHolderAgent
```

Test KeyHolder Agent with:

```shell
curl -X POST http://127.0.0.1:8095/message:send \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "content": [
        {
          "text": "provide_red_key"
        }
      ]
    }
  }'
```
Should return:
```shell
{
  "task": {
    "id": "97a46bcc-1247-45e9-b488-4489b695a63f",
    "contextId": "240a4d88-bc26-40d6-87a4-700168eaf8b9",
    "status": {
      "state": "TASK_STATE_COMPLETED",
      "timestamp": "2026-04-20T18:52:25.147912700Z"
    },
    "artifacts": [{
      "artifactId": "red-key",
      "name": "text/turtle",
      "parts": [{
        "text": "@prefix dyn: \u003chttp://example.org/dynamic-maze#\u003e .\n\n\u003chttp://127.0.1.1:8080/cells/37/36#key\u003e a dyn:RedKey;\n    dyn:fitsInLock \u003chttp://127.0.1.1:8080/cells/36/36\u003e;\n    dyn:keyValue \"redkey-1670\" .\n"
      }],
      "metadata": {
        "contentType": "text/turtle",
        "confidence": "0.99"
      }
    }],
    "history": [{
      "messageId": "msg-1",
      "contextId": "240a4d88-bc26-40d6-87a4-700168eaf8b9",
      "taskId": "97a46bcc-1247-45e9-b488-4489b695a63f",
      "role": "ROLE_USER",
      "content": [{
        "text": "provide_red_key"
      }],
      "metadata": {
      }
    }],
    "metadata": {
    }
  }
}
```
Note: `KeyHolderAgent` now adds an artifact metadata field `confidence` with value `0.99` for the returned red key artifact.

5. Sample A2A Client to retrieve the key value via A2A SDK

```shell
cd .\mase-server\
gradle runA2AKeySeekerAgent
```

## Maze RDF Type and Canvas Layout

Since the CCRS Maze consists of multiple zones which are NOT connected with each other by default (because of the locked-cells at their connection points which have the connecting triple removed in locked state) the maze-layout rendering requires an adjusted algorithm.

`MazeLayoutService` currently switches to the CCRS-specific layout algorithm only when the maze has type `maze:CcrsMaze`.
That CCRS layout is needed for this scenario because it also considers incoming cell references and disconnected components when calculating the coordinates sent to the viewer.

If `maze:CcrsMaze` is renamed to another type, for example `maze:AdvancedMaze`, the server falls back to the generic layout algorithm.
The Svelte canvas then only renders the first zone.

```trig
</maze> a ldp:BasicContainer , maze:AdvancedMaze , maze:CcrsMaze ;
```
