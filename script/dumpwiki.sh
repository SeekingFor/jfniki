#!/usr/bin/env sh

# TIP:
# You should be able to copy this script anywhere you want. Just make symlinks
# to the jfniki.jar and freenet.jar files in the same directory.

export set SCRIPT_DIR=`dirname $0`
. "${SCRIPT_DIR}/setup_env.sh"

${JAVA_CMD} -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.standalone.DumpWiki "$@"
