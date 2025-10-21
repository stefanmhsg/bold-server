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
./build/install/bold-server/bin/bold-server sim-UnsafeMaze & 
sleep 7.72; ./run.sh &
~/Programme/linked-data-fu/bin/ldfu.sh -i http://127.0.1.1:8080/cells/5 -i http://127.0.1.1:8080/cells/18 -p ./ldfuPrograms/unlock55.n3 -n 2500 &
#~/Programme/linked-data-fu/bin/ldfu.sh -i http://127.0.1.1:8080/cells/16 -i http://127.0.1.1:8080/cells/1 -p ./ldfuPrograms/switch55.n3 &
#~/Programme/linked-data-fu/bin/ldfu.sh -i http://127.0.1.1:8080/cells/21 -i http://127.0.1.1:8080/cells/22 -i http://127.0.1.1:8080/cells/16 -i http://127.0.1.1:8080/cells/17 -i http://127.0.1.1:8080/cells/20 -p ./ldfuPrograms/switch55.n3 &
#~/Programme/linked-data-fu/bin/ldfu.sh -i http://127.0.1.1:8080/cells/21 -i http://127.0.1.1:8080/cells/22 -i http://127.0.1.1:8080/cells/16 -i http://127.0.1.1:8080/cells/17 -i http://127.0.1.1:8080/cells/20 -p ./ldfuPrograms/switch55.n3 -n 5000 &
wait
