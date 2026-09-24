#!/usr/bin/env bash
set -euo pipefail
cd /workspace

echo "==> Installing contracts module to ~/.m2"
mvn -pl contracts -am install -DskipTests -q

echo "==> Installing frontend dependencies"
cd frontend
npm ci

echo "==> Generating OpenAPI client"
npm run generate:api
cd ..

echo "==> Pre-resolving backend Maven dependencies"
mvn -pl backend -am dependency:go-offline -q || true

echo "==> Installing cookidoo-service Python dependencies"
cd cookidoo-service
pip install --user -e .
cd ..

echo "==> Done."
