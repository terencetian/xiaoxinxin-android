#!/bin/bash
# 把 Android 正式包归档到 ~/Documents/小薪薪-发布版本/v<版本>/
# 用法：./tools/archive_apk.sh 1.9.2 [--rebuild]
set -euo pipefail

VER="${1:?用法: ./tools/archive_apk.sh <版本号> [--rebuild]}"
REBUILD="${2:-}"

export JAVA_HOME="${JAVA_HOME:-$HOME/dev-tools/amazon-corretto-17.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
GRADLE="${GRADLE:-$HOME/dev-tools/gradle-8.11.1/bin/gradle}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
BASE="$HOME/Documents/小薪薪-发布版本"

if [ "$REBUILD" = "--rebuild" ] || [ ! -f "$APK" ]; then
  echo "· 构建 release 包…"
  (cd "$ROOT" && "$GRADLE" assembleRelease --console=plain -q)
fi
[ -f "$APK" ] || { echo "✗ 找不到 $APK"; exit 1; }

AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
[ -x "$AAPT2" ] || AAPT2="$(ls "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | tail -1)"
BADGE="$("$AAPT2" dump badging "$APK" 2>/dev/null | head -1)"
PKG_VER="$(echo "$BADGE" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
PKG_CODE="$(echo "$BADGE" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")"
PKG_NAME="$(echo "$BADGE" | sed -n "s/package: name='\([^']*\)'.*/\1/p")"

if [ "$PKG_VER" != "$VER" ]; then
  echo "✗ 版本不符：APK 实际为 v${PKG_VER}，而非 v${VER} —— 已中止，避免张冠李戴"
  exit 1
fi

DEST="$BASE/v$VER"
mkdir -p "$DEST"
TARGET="$DEST/小薪薪-v$VER-android-universal.apk"
cp "$APK" "$TARGET"

SIZE=$(du -h "$TARGET" | cut -f1 | tr -d ' ')
SHA=$(shasum -a 256 "$TARGET" | cut -d' ' -f1)

echo "✓ 已归档 $TARGET"
echo "  包名     $PKG_NAME"
echo "  版本     v$PKG_VER (versionCode $PKG_CODE)"
echo "  体积     $SIZE"
echo "  sha256   $SHA"

IDX="$BASE/版本索引.md"
if [ -f "$IDX" ] && ! grep -q "| v$VER | $PKG_CODE | Android" "$IDX"; then
  ROW="| v$VER | $PKG_CODE | Android / universal | $(date +%Y-%m-%d) | v$VER | v$VER | 归档于 $(date +%H:%M) |"
  printf '%s\n' "$ROW" >> "$IDX"
  echo "· 已在 版本索引.md 追加一行"
else
  echo "· 索引里已有对应记录，未重复追加"
fi
