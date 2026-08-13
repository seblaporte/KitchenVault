#!/usr/bin/env bash
set -euo pipefail
cd /workspace

SESSION=kitchenvault
JDWP_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

tmux kill-session -t "$SESSION" 2>/dev/null || true

tmux new-session -d -s "$SESSION" -n backend -c /workspace
tmux send-keys -t "$SESSION:backend" \
  "mvn spring-boot:run -pl backend -Dspring-boot.run.profiles=devcontainer -Dspring-boot.run.jvmArguments='${JDWP_ARGS}'" C-m

tmux new-window -t "$SESSION" -n frontend -c /workspace/frontend
tmux send-keys -t "$SESSION:frontend" \
  "npm run generate:api && npx ng serve --host 0.0.0.0" C-m

echo "==> tmux session '$SESSION' démarrée (backend + frontend). Attacher avec: tmux attach -t $SESSION"
