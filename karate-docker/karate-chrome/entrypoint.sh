#!/bin/bash
set -x -e
envsubst < supervisord.conf > /etc/supervisor/conf.d/supervisord.conf
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
