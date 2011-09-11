#!/usr/bin/env sh

# CAREFUL: This tool can delete everything in
# the current directory if you ask it to.
# Use it at your own peril.

export set FCP_HOST="127.0.0.1"
export set FCP_PORT="19481"

# Deal with symlinks.
export set FULL_SCRIPT_PATH=`readlink -e $0`
export set SCRIPT_DIR=`dirname ${FULL_SCRIPT_PATH}`

# This doesn't work if you symlink to the script.
# That's why I used the hacks above.
#export set SCRIPT_DIR=`dirname $0`

. "${SCRIPT_DIR}/setup_env.sh"

${JAVA_CMD} -Dwormarc.cli.fms.host=${FCP_HOST} -Dwormarc.cli.fms.port=${FCP_PORT} \
            -classpath ${JAR_FILE}:${FN_JAR_FILE} wormarc.cli.CLI "$@"

