# CCRS Scenario

Data: [CcrsMaze.trig](mase-server/data/CcrsMaze.trig)

Infrastructure Agents: [CcrsAgent.java](mase-server/src/main/java/org/maze/examples/CcrsAgent.java)

## Run with Gradle

1. Start the server:

```shell
cd .\mase-server\
gradle runMase --args="sim-CcrsMaze"
```

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
