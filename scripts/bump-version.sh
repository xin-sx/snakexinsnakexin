#!/usr/bin/env bash
# 用法：
#   bash scripts/bump-version.sh patch   # 1.0.0 -> 1.0.1
#   bash scripts/bump-version.sh minor   # 1.0.0 -> 1.1.0
#   bash scripts/bump-version.sh major   # 1.0.0 -> 2.0.0
# 自动把 version.properties 里的 VERSION_NAME 抬一档，VERSION_CODE +1。
set -e

LEVEL="${1:-patch}"
PROP="$(dirname "$0")/../version.properties"

if [ ! -f "$PROP" ]; then
  echo "version.properties 不存在：$PROP" >&2
  exit 1
fi

NAME=$(grep '^VERSION_NAME=' "$PROP" | cut -d= -f2)
MAJOR=$(echo "$NAME" | cut -d. -f1)
MINOR=$(echo "$NAME" | cut -d. -f2)
PATCH=$(echo "$NAME" | cut -d. -f3)

case "$LEVEL" in
  major) MAJOR=$((MAJOR+1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR+1)); PATCH=0 ;;
  patch|"") PATCH=$((PATCH+1)) ;;
  *) echo "未知级别：$LEVEL（用 patch / minor / major）" >&2; exit 1 ;;
esac

NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
OLD_CODE=$(grep '^VERSION_CODE=' "$PROP" | cut -d= -f2)
NEW_CODE=$((OLD_CODE+1))

# 写回文件
sed -i.bak \
  -e "s|^VERSION_NAME=.*|VERSION_NAME=${NEW_NAME}|" \
  -e "s|^VERSION_CODE=.*|VERSION_CODE=${NEW_CODE}|" \
  "$PROP"
rm -f "${PROP}.bak"

echo "✓ VERSION_NAME: $NAME -> ${NEW_NAME}"
echo "✓ VERSION_CODE: $OLD_CODE -> ${NEW_CODE}"
echo "记得：git add version.properties && git commit -m \"bump version to ${NEW_NAME}\""
