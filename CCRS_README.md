Dev - deafults to "UnsafeMaze" (loads rules/Global per default)
```shell script
gradle runBold
```

Specifing maze as argument (loads rules/Global per default)
```shell script
gradle runBold --args="sim-MidMaze"
```

Specifing maze AND rules as argument (loads rules/Global per default AND rules/Global/Stigmergy)
```shell script
gradle runBold --args="sim-UnsafeMaze  Stigmergy"
```

Sim
```bash
./setup-MidMaze.sh
```

Docker Sim
```shell script
docker run -p 8080:8080 -e TASKNAME=setup-MidMaze -it bold-server
```
