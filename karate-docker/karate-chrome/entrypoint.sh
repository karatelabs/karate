#!/bin/bash
set -x -e
if [ -z "$KARATE_JOBURL" ]
  then
    export KARATE_OPTIONS="-h"
    export KARATE_START="false"
  else
    export KARATE_START="true"
    export KARATE_OPTIONS="-j $KARATE_JOBURL"
fi
if [ -z "$KARATE_SOCAT_START" ]
  then
	export KARATE_SOCAT_START="false"
	export KARATE_CHROME_PORT="9222"
  else
	export KARATE_SOCAT_START="true"
	export KARATE_CHROME_PORT="9223"
fi
[ -z "$KARATE_WIDTH" ] && export KARATE_WIDTH="1280"
[ -z "$KARATE_HEIGHT" ] && export KARATE_HEIGHT="720"
exec /usr/bin/supervisord
