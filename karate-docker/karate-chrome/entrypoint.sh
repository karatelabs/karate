#!/bin/bash
set -x -e
if [ -z "$KARATE_JOBURL" ]
  then
    [ -z "$KARATE_OPTIONS" ] && export KARATE_OPTIONS="-h"
  else
    export KARATE_OPTIONS="-j $KARATE_JOBURL"
fi
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf

