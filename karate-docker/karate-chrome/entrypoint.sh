#!/bin/bash
set -x -e
if [ -z "$KARATE_JOBURL" ]
  then
    export KARATE_FFMPEG_START="true"
    export KARATE_OPTIONS="-h"
    export KARATE_START="false"
  else
    export KARATE_FFMPEG_START="false"
    export KARATE_START="true"
    export KARATE_OPTIONS="-j $KARATE_JOBURL"
fi
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf

