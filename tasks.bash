#!/bin/bash

# cd to the script dir
pushd "$(dirname "$0")"

date
bash disk-read.bash
bash disk-write.bash
bash net-send.bash
bash net-receive.bash

popd 
