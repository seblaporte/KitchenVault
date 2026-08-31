#!/usr/bin/env bash
set -euo pipefail
cd /workspace

if [ ! -f .env ]; then
  cp .env.example .env
  echo "==> .env créé depuis .env.example — à compléter pour un usage local"
fi

echo "==> Verifying Docker socket access (Testcontainers requirement)"
docker version --format '{{.Server.Version}}' >/dev/null 2>&1 \
  && echo "    docker.sock OK" \
  || echo "    WARNING: docker.sock inaccessible — les tests backend (Testcontainers) échoueront"

echo "==> Ready."
