#!/usr/bin/env bash

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
PARENT_DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
LIBS=${PARENT_DIR}/lib
MODELS_DIST=${PARENT_DIR}/dist/models

mkdir -p ${PARENT_DIR}/dist
mkdir -p ${MODELS_DIST}

find ${PARENT_DIR}/models -name '*.java' | xargs javac -source 1.8 -target 1.8 -d ${MODELS_DIST}

for d in `ls -d ${PARENT_DIR}/*/`
do
    # Skip dist directory.
    if [[ `basename ${d}` == "dist" || `basename ${d}` == "lib" || `basename ${d}` == "models" ]]
    then
        continue
    fi

    BUILD_DIR=${PARENT_DIR}/dist/`basename ${d}`
    mkdir -p ${BUILD_DIR}
    find ${d} -name '*.java' | xargs javac -source 1.8 -target 1.8 -cp ${MODELS_DIST}:${LIBS}/android.jar -d ${BUILD_DIR}
done
