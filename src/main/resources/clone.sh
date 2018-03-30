#!/bin/bash

original_dir=$(pwd)

echo "moving to TEMP_DIR_REPLACEMENT/code"
mkdir TEMP_DIR_REPLACEMENT/code
cd TEMP_DIR_REPLACEMENT/code

git init

git config core.sparsecheckout true

extensions=( "html" "txt" "md" "gif" "vsl" "sample" "serialized" "exe" "svg" "bz2" "zip" "jar" "dll" "tar" "tgz" "gz" "egg" "tbz2" "png" "ppt" "pptx" "pdf" "file" "idx" "lock" "vm" "whl" "doc" "docx" "xls" "xlsx" "csv")

for ext in "${extensions[@]}"
do
    echo '!*.'$ext >> .git/info/sparse-checkout
done

echo '/*' >> .git/info/sparse-checkout

git remote add -t master --no-tags origin VCS_URL_REPLACEMENT
git fetch --depth=1 origin
git checkout master

echo "moving back to $original_dir"
cd $original_dir
