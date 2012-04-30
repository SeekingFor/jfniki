#!/usr/bin/env sh

# Helper script to check the serialvers of classes
# used to implement persistence.

export set SCRIPT_DIR=`dirname $0`
. "${SCRIPT_DIR}/setup_env.sh"

echo "Checking from the jar file: ${FN_JAR_FILE}"
serialver -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.wiki.PersistedState
serialver -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.wiki.Configuration
serialver -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.wiki.WikiInfo
serialver -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.wiki.SiteTheme
serialver -classpath ${JAR_FILE}:${FN_JAR_FILE} fniki.wiki.RAMFileInfo