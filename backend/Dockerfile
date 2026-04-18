# ── Stage 1 : compilation native ────────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:25 AS builder
WORKDIR /build

# Installer Maven (image Oracle Linux, pas de mvnw dans ce projet)
RUN microdnf install -y maven && microdnf clean all

# Copier les fichiers Maven en premier pour profiter du cache des dépendances
COPY pom.xml ./
COPY contracts/ contracts/
COPY backend/ backend/

# Build natif (contracts + backend)
RUN mvn install -pl contracts --no-transfer-progress -q \
 && mvn -Pnative native:compile -pl backend --no-transfer-progress -q

# ── Stage 2 : image minimale ─────────────────────────────────────────────────
FROM ubuntu:24.04
RUN apt-get update \
    && apt-get install -y --no-install-recommends libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/backend/target/kitchen-vault /app/kitchen-vault

EXPOSE 8080
ENTRYPOINT ["/app/kitchen-vault"]
