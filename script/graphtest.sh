#!/usr/bin/env sh

# Script to run GraphLog.java code from a text file for debugging.

export set SCRIPT_DIR=`dirname $0`
. "${SCRIPT_DIR}/setup_env.sh"

${JAVA_CMD} -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.wiki.GraphLog "$@"
