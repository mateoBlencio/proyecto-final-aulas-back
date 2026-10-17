#!/usr/bin/env bash
# SessionStart hook. Checks the skills, CLIs and plugins listed in
# .claude/dependencies.json and prints the missing ones, which Claude Code
# adds to the session context. Prints nothing when everything is present.
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

manifest=".claude/dependencies.json"
if ! command -v jq >/dev/null 2>&1; then
  echo "Falta jq: los hooks de .claude/hooks/ no funcionan sin él. Instalar: sudo dnf install jq (o apt install jq). Preguntale al usuario si continúa sin jq o lo instala."
  exit 0
fi
[ -f "$manifest" ] || exit 0

installed_plugins="$HOME/.claude/plugins/installed_plugins.json"

plugin_enabled() {
  local key="$1" f
  for f in "$HOME/.claude/settings.json" .claude/settings.json .claude/settings.local.json; do
    [ -f "$f" ] && jq -e --arg k "$key" '.enabledPlugins[$k] == true' "$f" >/dev/null 2>&1 && return 0
  done
  return 1
}

plugin_installed() {
  [ -f "$installed_plugins" ] && jq -e --arg k "$1" '.plugins | has($k)' "$installed_plugins" >/dev/null 2>&1
}

missing=()

while IFS=$'\x1f' read -r name used install; do
  [ -f ".claude/skills/$name/SKILL.md" ] || [ -f "$HOME/.claude/skills/$name/SKILL.md" ] \
    || missing+=("- skill $name (la usa: $used). Instalar: $install")
done < <(jq -r '.skills[]? | [.name, .usedBy, .install] | join("\u001f")' "$manifest")

while IFS=$'\x1f' read -r name cmd only used install; do
  if [ -n "$only" ] && ! plugin_enabled "$only"; then continue; fi
  if [[ "$cmd" == */* ]]; then
    [ -x "$cmd" ] && continue
  else
    command -v "$cmd" >/dev/null 2>&1 && continue
  fi
  missing+=("- CLI $name (la usa: $used). Instalar: $install")
done < <(jq -r '.clis[]? | [.name, .command, (.onlyIfPlugin // ""), .usedBy, .install] | join("\u001f")' "$manifest")

while IFS=$'\x1f' read -r name used install; do
  plugin_installed "$name" || missing+=("- plugin $name (lo usa: $used). Instalar: $install")
done < <(jq -r '.plugins[]? | [.name, .usedBy, .install] | join("\u001f")' "$manifest")

[ ${#missing[@]} -eq 0 ] && exit 0

echo "Dependencias faltantes según $manifest:"
printf '%s\n' "${missing[@]}"
echo "Antes de una tarea que necesite alguna, preguntale al usuario si continúa sin ella o la instala (CLAUDE.md, \"Skills y herramientas\"). No instales nada sin su ok."
exit 0
