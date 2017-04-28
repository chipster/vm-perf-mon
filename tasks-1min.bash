#!/bin/bash

# cd to the script dir
pushd "$(dirname "$0")"

date
bash disk-read-small.bash
bash disk-write-small.bash

popd 
