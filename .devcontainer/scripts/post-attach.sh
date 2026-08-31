#!/usr/bin/env bash
cat <<'EOF'
KitchenVault devcontainer prêt — backend et frontend démarrent automatiquement (postStartCommand) dans une session tmux.

  Backend  (Spring Boot, profil devcontainer): déjà lancé, debug JDWP sur le port 5005 -> http://localhost:8080
  Frontend (Angular, ng serve):                déjà lancé                              -> http://localhost:4200
  cookidoo-service:                            déjà démarré                            -> http://localhost:8001
  Postgres:                                    déjà démarré                            -> localhost:5432 (cookidoo/cookidoo)
  pgAdmin (à la demande):  docker compose -f compose.yaml up -d pgadmin  -> http://localhost:5050

  Voir les logs / suivre les process :  tmux attach -t kitchenvault   (Ctrl+B puis D pour se détacher sans les arrêter)
  Relancer manuellement les deux :      bash .devcontainer/scripts/start-dev.sh

  Débogueur :
    VS Code / Codespaces : onglet Debug -> "Attach to Backend (5005)" (config fournie dans .vscode/launch.json)
    IntelliJ Gateway :      Run/Debug Configurations -> "Attach: Backend (5005)" (fournie dans .idea/runConfigurations)

  Tests backend (nécessite le socket Docker pour Testcontainers):
    mvn test -pl backend

  Secrets : éditer .env à la racine pour un usage local (gitignored).
  En Codespaces, configurer OVH_AI_ENDPOINTS_ACCESS_TOKEN / COOKIDOO_* comme Codespaces secrets.
EOF
