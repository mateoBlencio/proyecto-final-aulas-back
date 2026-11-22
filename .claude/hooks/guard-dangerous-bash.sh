#!/usr/bin/env bash
# PreToolUse hook, matcher Bash. Denies, including chained forms:
# force-push to main/develop (flag or +refspec), a commit with
# Co-Authored-By: Claude, a PR body with the Claude Code signature, and
# destructive commands (rm -rf, git reset --hard, git clean -f,
# git checkout|restore ., find -delete).
set -euo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}"

command="$(jq -r '.tool_input.command // empty')"
[ -z "$command" ] && exit 0

deny() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

is_protected_branch() {
  grep -qE '\b(main|develop)\b' <<<"$command" && return 0
  # No named branch in the command (a bare "git push -f"): assume it
  # targets the current branch, and only that branch decides.
  local leftover
  leftover="$(sed -E 's/\bgit\b|\bpush\b|--force(-with-lease)?\b|-f\b|--set-upstream\b|-u\b|\borigin\b|\bupstream\b//g' <<<"$command")"
  leftover="$(tr -s '[:space:]' ' ' <<<"$leftover")"
  leftover="${leftover// /}"
  [ -n "$leftover" ] && return 1
  local branch
  branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
  [[ "$branch" == "main" || "$branch" == "develop" ]]
}

if grep -qE '\bgit[[:space:]]+push\b' <<<"$command" \
  && grep -qE '(--force(-with-lease)?\b|(^|[[:space:]])-f\b|[[:space:]]\+[[:alnum:]])' <<<"$command" \
  && is_protected_branch; then
  deny "Force-push a main/develop bloqueado por hook. Si hace falta, pedile al usuario que lo corra directamente."
fi

if grep -qE '\bgit[[:space:]]+commit\b' <<<"$command" \
  && grep -qiE 'co-authored-by:[[:space:]]*claude' <<<"$command"; then
  deny "Este repo no suma coautoria de Claude en los commits, ver CLAUDE.md. Sacá la línea Co-Authored-By y reintentá."
fi

if grep -qE '\bgh[[:space:]]+pr[[:space:]]+(create|edit)\b' <<<"$command" \
  && grep -qiE 'generated with \[?claude code|claude\.com/claude-code' <<<"$command"; then
  deny "Este repo no lleva la firma de Claude Code en los PR, ver CLAUDE.md. Sacala del body y reintentá."
fi

destructive_patterns=(
  '\brm[[:space:]]+(-[[:alnum:]]*r[[:alnum:]]*f[[:alnum:]]*|-[[:alnum:]]*f[[:alnum:]]*r[[:alnum:]]*)\b'
  '\brm\b[^&|;]*--recursive[^&|;]*--force'
  '\brm\b[^&|;]*--force[^&|;]*--recursive'
  '\brm\b[^&|;]*[[:space:]]-[[:alnum:]]*r[^&|;]*[[:space:]]-[[:alnum:]]*f'
  '\brm\b[^&|;]*[[:space:]]-[[:alnum:]]*f[^&|;]*[[:space:]]-[[:alnum:]]*r'
  '\bgit[[:space:]]+reset[[:space:]]+--hard\b'
  '\bgit[[:space:]]+clean[[:space:]]+-[[:alnum:]]*f[[:alnum:]]*\b'
  '\bgit[[:space:]]+clean\b[^&|;]*--force'
  '\bgit[[:space:]]+(checkout|restore)\b[^&|;]*[[:space:]]\.([[:space:]]|$)'
  '\bfind\b[^&|;]*[[:space:]]-delete\b'
)

for pattern in "${destructive_patterns[@]}"; do
  if grep -qE "$pattern" <<<"$command"; then
    deny "Comando destructivo bloqueado (rm -rf, git reset --hard, git clean -f, git checkout|restore ., find -delete). Si es intencional, pedile al usuario que lo corra."
  fi
done

exit 0
