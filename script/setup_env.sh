#!/usr/bin/env sh

#SCRIPT_DIR is setup by the script calling this script

export set JAR_NAME="jfniki.jar"

export set JAR_PATH="${SCRIPT_DIR}/../build/jar"
export set JAR_FILE="${JAR_PATH}/${JAR_NAME}"
if [ ! -f ${JAR_FILE} ];
then
    export set JAR_PATH=${SCRIPT_DIR}
    export set JAR_FILE="${JAR_PATH}/${JAR_NAME}"
    if [ ! -f ${JAR_FILE} ];
    then
        echo "Looked in:"
        echo "${SCRIPT_DIR}/../build/jar/${JAR_NAME}"
        echo "and"
        echo "${JAR_FILE}"
        echo
        echo "but still can't find the jar file!"
        echo "Maybe run: ant jar?"
        exit -1
    fi
fi

echo "Using fniki.jar: ${JAR_FILE}"
echo

export set FN_JAR_NAME="freenet.jar"
export set FN_JAR_PATH="${SCRIPT_DIR}/../alien/libs"
export set FN_JAR_FILE="${FN_JAR_PATH}/${FN_JAR_NAME}"
if [ ! -f ${FN_JAR_FILE} ];
then
    export set FN_JAR_PATH=${SCRIPT_DIR}
    export set FN_JAR_FILE="${FN_JAR_PATH}/${FN_JAR_NAME}"
    if [ ! -f ${FN_JAR_FILE} ];
    then
        echo "Looked in:"
        echo "${SCRIPT_DIR}/../alien/libs/${FN_JAR_NAME}"
        echo "and"
        echo "${FN_JAR_FILE}"
        echo
        echo "but still can't find the freenet.jar file!"
        echo "Not sure what's going on. :-("
        exit -1
    fi
fi

echo "Using freenet.jar: ${FN_JAR_FILE}"
echo
export set JAVA_CMD="java"
