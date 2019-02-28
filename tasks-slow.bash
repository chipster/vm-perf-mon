#!/bin/bash

# cd to the script dir
pushd "$(dirname "$0")"

echo "tasks-slow"

date
bash disk-read-old-file-ra256.bash 2>&1
bash disk-read-old-file.bash 2>&1

pushd java/vm-perf-mon/bin/; ./vm-perf-mon ../conf.yaml; popd

popd 
