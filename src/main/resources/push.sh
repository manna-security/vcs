#!/bin/bash

original_dir=$(pwd)

echo "moving to TEMP_DIR_REPLACEMENT/code"
cd TEMP_DIR_REPLACEMENT/code

# add all tracked files
# (only supports edits currently, if adds are necessary - this will need to be extended)
GIT_SSH_COMMAND='ssh -i PRIVATE_KEY_LOCATION'
git push origin master

echo "moving back to $original_dir"
cd $original_dir
