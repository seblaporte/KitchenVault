FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Java 25 + Maven sont installés par la Feature "java" (voir devcontainer.json) via SDKMAN.
# Ce Dockerfile reste minimal : uniquement les paquets additionnels non couverts par les Features.
RUN apt-get update && apt-get install -y --no-install-recommends \
      postgresql-client \
      tmux \
    && rm -rf /var/lib/apt/lists/*
