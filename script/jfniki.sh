#!/usr/bin/env sh

# TIP:
# You should be able to copy this script anywhere you want and make a symlink
# to the jfniki.jar file in your build directory, in the directory you copy it to.

# TIP:
# Look in the XML file generate by FMS when you export and identity on
# the "Local Identities"  page to find these values.

# MUST set this to post. i.e. you can run read only without it if you want.
# The <PrivateKey> value for the FMS identity you want post wiki submissions with.
export set PRIVATE_FMS_SSK="SSK@XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX,XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX,AQECAAE/"

# MUST set this to read new version from FMS. i.e. Don't try to run with setting it!
# The correponding <name> value for that private key.
export set FMS_ID="YOUR_FMS_HERE"

# FAIL in an obvious way until properly configured.
echo "MANUAL CONFIGURATION REQUIRED!"
echo "Edit PRIVATE_FMS_SSK and FMS_ID in this script, then comment out these 3 lines."
exit -1

export set ENABLE_IMAGES=1
export set LISTEN_PORT=8083

export set JAR_NAME="jfniki.jar"
# Look for the jfniki.jar file in the build dir.
# If you want to move it to somewhere else, modify the line below.
#export set JAR_PATH="${0%%/*}/../build/jar"
export set SCRIPT_DIR=`dirname $0`

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

# FCP configuration
export set FCP_HOST="127.0.0.1"
export set FCP_PORT=9481

# FMS configuration
export set FMS_HOST="127.0.0.1"
export set FMS_PORT=1119

export set FMS_GROUP="biss.test000"
export set WIKI_NAME="testwiki"

# fproxy configuration.
export set FPROXY_PREFIX="http://127.0.0.1:8888/"

export set JAVA_CMD="java"

${JAVA_CMD} -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.standalone.ServeHttp \
    ${LISTEN_PORT} \
    ${FCP_HOST} \
    ${FCP_PORT} \
    ${FMS_HOST} \
    ${FMS_PORT} \
    ${PRIVATE_FMS_SSK} \
    "${FMS_ID}" \
    ${FMS_GROUP} \
    ${WIKI_NAME} \
    ${FPROXY_PREFIX} \
    ${ENABLE_IMAGES} \
    $1
