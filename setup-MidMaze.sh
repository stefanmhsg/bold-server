#!/bin/bash
trap terminate SIGINT
terminate(){
    echo Ending simulation
    pkill -SIGKILL -P $$
    pkill -f linked-data-fu
    echo $?
    exit
}
#the rest of code
./build/install/bold-server/bin/bold-server sim-MidMaze & 
sleep 5; ./run.sh &
sleep 7; ~/Programme/linked-data-fu/bin/ldfu.sh -i http://127.0.1.1:8080/cells/3/2 -i http://127.0.1.1:8080/cells/28/13 -i http://127.0.1.1:8080/cells/36/36 -i http://127.0.1.1:8080/cells/50/59 -p ./ldfuPrograms/unlock250.n3 -n 2800 &
sleep 7; ~/Programme/linked-data-fu/bin/ldfu.sh  -i http://127.0.1.1:8080/cells/32/18 -i http://127.0.1.1:8080/cells/42/1 -i http://127.0.1.1:8080/cells/36/22 -i http://127.0.1.1:8080/cells/41/3 -i http://127.0.1.1:8080/cells/41/43 -i http://127.0.1.1:8080/cells/17/1 -i http://127.0.1.1:8080/cells/52/71 -i http://127.0.1.1:8080/cells/17/4 -i http://127.0.1.1:8080/cells/62/89 -i http://127.0.1.1:8080/cells/1/243 -p ./ldfuPrograms/switch250.n3 &
#sleep 7; ~/Programme/linked-data-fu/bin/ldfu.sh  -i http://127.0.1.1:8080/cells/32/18  -i http://127.0.1.1:8080/cells/32/19  -i http://127.0.1.1:8080/cells/32/17 -i http://127.0.1.1:8080/cells/33/19 -i http://127.0.1.1:8080/cells/31/19 -i http://127.0.1.1:8080/cells/36/22 -i http://127.0.1.1:8080/cells/36/23 -i http://127.0.1.1:8080/cells/36/21  -i http://127.0.1.1:8080/cells/35/23 -i http://127.0.1.1:8080/cells/37/23 -i http://127.0.1.1:8080/cells/37/28 -i http://127.0.1.1:8080/cells/37/29 -i http://127.0.1.1:8080/cells/37/27 -i http://127.0.1.1:8080/cells/37/30 -i http://127.0.1.1:8080/cells/38/29 -i http://127.0.1.1:8080/cells/41/43 -i http://127.0.1.1:8080/cells/42/43 -i http://127.0.1.1:8080/cells/41/44 -i http://127.0.1.1:8080/cells/42/42 -i http://127.0.1.1:8080/cells/43/43 -p ./ldfuPrograms/switch250.n3 -n 5000 &
wait





#./build/install/bold-server/bin/bold-server sim-UnsafeMaze
