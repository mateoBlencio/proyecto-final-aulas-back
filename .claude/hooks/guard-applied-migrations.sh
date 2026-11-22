#!/usr/bin/env bash
# PreToolUse hook, matcher Edit|Write. Denies changes to a Flyway migration
# already merged into origin/develop or origin/main: an applied migration is
# never edited, a new one is added instead (CLAUDE.md, "Migraciones").
set -euo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}"

path="$(jq -r '.tool_input.file_path // empty')"
case "$path" in
  */src/main/resources/db/migration/V*.sql | src/main/resources/db/migration/V*.sql) ;;
  *) exit 0 ;;
esac

rel="${path#"$PWD"/}"
merged=false
for ref in origin/develop origin/main; do
  git cat-file -e "$ref:$rel" 2>/dev/null && merged=true
done

if $merged; then
  jq -n '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: "Migración ya mergeada: no se edita. Agregá una migración nueva con el número siguiente."
    }
  }'
fi
exit 0
