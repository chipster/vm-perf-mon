#!/bin/bash

# cd to the script dir
pushd "$(dirname "$0")"

date
bash disk-read-old-file.bash

popd 
