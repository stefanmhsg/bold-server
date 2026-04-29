# CCRS Scenario

Data: [CcrsMaze.trig](mase-server/data/CcrsMaze.trig)

Infrastructure Agents: [CcrsAgent.java](mase-server/src/main/java/org/maze/examples/CcrsAgent.java)

## Run with Gradle

1. Start the server:

```shell
cd .\mase-server\
gradle runMase --args="sim-CcrsMaze"
```

Variants: in [sim-CcrsMaze.properties](mase-server/sim-CcrsMaze.properties) select the dataset version

- sim-CcrsMazeV1: redkey removed, use with key-holder-agent

- sim-CcrsMazeV2: redkey is placed in Cell

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
