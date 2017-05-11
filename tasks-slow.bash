#!/bin/bash

# cd to the script dir
pushd "$(dirname "$0")"

date
bash disk-read-old-file.bash
bash disk-read-old-file-ra256.bash

popd 
