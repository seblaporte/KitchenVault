#!/bin/sh
set -e
jq -n --arg apiUrl "${API_URL:-}" '{"apiUrl": $apiUrl}' \
  > /usr/share/nginx/html/assets/config.json
exec nginx -g 'daemon off;'
