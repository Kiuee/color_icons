#!/usr/bin/env bash
# 本地打包发布脚本：将本地构建/签名好的 APK 推送到 GitHub Release
# 用法:
#   ./release.sh v1.1                     # 自动查找 APK
#   ./release.sh v1.1 D:/path/xx.apk     # 指定（已签名的）APK 文件
set -euo pipefail

TAG="${1:?用法: ./release.sh <tag> [apk路径]}"
shift || true

command -v gh >/dev/null 2>&1 || { echo "❌ 未安装 gh CLI：https://cli.github.com/"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "❌ gh 未登录，请先执行: gh auth login"; exit 1; }

# APK 路径：优先命令行指定，其次 app/release/，最后 gradle 默认输出
APK="${1:-}"
if [ -z "$APK" ]; then
  for c in "app/release/app-release.apk" "app/build/outputs/apk/release/app-release.apk"; do
    if [ -f "$c" ]; then APK="$c"; break; fi
  done
fi
[ -n "$APK" ] && [ -f "$APK" ] || { echo "❌ 找不到 APK（可手动指定: ./release.sh $TAG <apk路径>）"; exit 1; }

echo "==> 目标 tag: $TAG"
echo "==> APK: $APK ($(du -h "$APK" | cut -f1))"

# 同步 update.json 的版本号（供应用内检查更新读取），有改动则提交推送
if [ -f update.json ]; then
  VER=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)
  if [ -n "$VER" ]; then
    sed -i "s/\"versionName\": *\"[^\"]*\"/\"versionName\": \"$VER\"/" update.json
    if ! git diff --quiet update.json; then
      git add update.json
      git commit -m "chore: update.json -> $VER"
      git push
      echo "==> 已更新并推送 update.json (v$VER)"
    fi
  fi
fi

# 发布：已存在同名 Release 则覆盖附件，否则创建
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "==> Release $TAG 已存在，覆盖上传 APK"
  gh release upload "$TAG" "$APK" --clobber
else
  gh release create "$TAG" "$APK" --title "color_icons $TAG" --generate-notes
fi

echo "✅ 发布完成: https://github.com/Kiuee/color_icons/releases/tag/$TAG"
