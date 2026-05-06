#!/bin/sh
set -e
# Génère la config nginx avec le BACKEND_URL injecté au démarrage du conteneur
# envsubst '$BACKEND_URL' substitue uniquement cette variable,
# laissant intacts les $host, $uri, etc. propres à nginx
envsubst '$BACKEND_URL' \
  < /etc/nginx/conf.d/default.conf.template \
  > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
