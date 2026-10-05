#!/usr/bin/env bash
# Register real LLM compute nodes on the SANDBOX test daemon so council runs,
# automaton planning (decompose) and LLM sessions get real replies in app tests.
#
# Never point this at the production daemon. Node names/URLs are NOT stored in
# the repo: pass them in DW_TEST_LLM_NODES or a local env file outside the repo,
# e.g. /home/dmz/workspace/.datawatch-test-<run>/llm-nodes.env:
#
#   DW_TEST_LLM_NODES="nodeA=http://<host-a>:11434,nodeB=http://<host-b>:11434"
#   DW_TEST_LLM_MODEL="qwen3:1.7b"        # small + fast; must exist on every node
#
# Usage: scripts/sandbox-seed-llms.sh <sandbox-base-url> <token> [env-file]
# Creates compute node <name> (kind ollama) and LLM ollama-<name> per node, then
# sets council.llm_ref and autonomous.planning_backend/model to the first node.
set -euo pipefail
BASE="${1:?sandbox base URL, e.g. https://127.0.0.1:18543}"
TOKEN="${2:?sandbox token}"
ENV_FILE="${3:-}"
[ -n "$ENV_FILE" ] && [ -f "$ENV_FILE" ] && . "$ENV_FILE"
: "${DW_TEST_LLM_NODES:?set DW_TEST_LLM_NODES=name=url,... (see header)}"
MODEL="${DW_TEST_LLM_MODEL:-qwen3:1.7b}"

case "$BASE" in
  *:8443*|*:8080*) echo "refusing: $BASE looks like the production daemon" >&2; exit 1 ;;
esac

api() { curl -sk -m 30 -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$@"; }

first=""
IFS=',' read -ra NODES <<< "$DW_TEST_LLM_NODES"
for entry in "${NODES[@]}"; do
  name="${entry%%=*}"; url="${entry#*=}"
  [ -z "$first" ] && first="$name"
  code=$(api -X POST "$BASE/api/compute/nodes" -d "{\"name\":\"$name\",\"kind\":\"ollama\",\"address\":\"$url\"}")
  echo "compute node $name: $code"
  code=$(api -X POST "$BASE/api/llms" -d "{\"name\":\"ollama-$name\",\"kind\":\"ollama\",\"model\":\"$MODEL\",\"compute_nodes\":[\"$name\"],\"output_mode\":\"chat\",\"input_mode\":\"tmux\"}")
  echo "llm ollama-$name ($MODEL): $code"
done

# The council orchestrator resolves its LLM at daemon start (default ref "ollama");
# a live council.llm_ref change isn't picked up until restart, so also register
# the default name on the first node.
code=$(api -X POST "$BASE/api/llms" -d "{\"name\":\"ollama\",\"kind\":\"ollama\",\"model\":\"$MODEL\",\"compute_nodes\":[\"$first\"],\"output_mode\":\"chat\",\"input_mode\":\"tmux\"}")
echo "llm ollama (council default) → $first: $code"
code=$(api -X PUT "$BASE/api/config" -d "{\"council.llm_ref\":\"ollama-$first\",\"autonomous.planning_backend\":\"ollama-$first\",\"autonomous.planning_model\":\"$MODEL\"}")
echo "council + planning → ollama-$first: $code"
code=$(api -X POST "$BASE/api/llms/ollama-$first/test" -d "{}")
echo "llm test ollama-$first: $code"
