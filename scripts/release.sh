#!/usr/bin/env bash
# 配布用の APK とリリースノートを dist/ に作る。GitHub Release を作る手順は docs/release.md。
#
#   scripts/release.sh
#
# 版は app/build.gradle.kts の versionName から読む。次のどれかに当たると止まる:
# - 作業ツリーに commit していない変更がある（APK を commit と同じ中身から作るため）
# - CHANGELOG.md にその版の節（## [x.y.z]）が無い
# - その版の tag（vx.y.z）が既にある
# ユニットテストを走らせてから assembleDebug でビルドする。
set -euo pipefail

cd "$(dirname "$0")/.."

die() { echo "release.sh: $*" >&2; exit 1; }

version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
[[ -n $version ]] || die "app/build.gradle.kts から versionName を読めない"
tag="v$version"
name="sabera-acchi-muite-hoi-$version"

[[ -z $(git status --porcelain) ]] || die "commit していない変更がある"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then die "tag ${tag} が既にある（versionName と versionCode を上げる）"; fi

# CHANGELOG の「## [x.y.z]」から、次の「## [」かリンク定義（[x.y.z]: URL）の手前までを、その版の変更として使う
changes=$(awk -v v="$version" '
  index($0, "## [" v "]") == 1 { on = 1; next }
  on && (/^## \[/ || /^\[[^]]+\]: /) { exit }
  on { print }
' CHANGELOG.md)
[[ -n ${changes//[[:space:]]/} ]] || die "CHANGELOG.md に ## [$version] の節が無い"

./gradlew --console=plain -q testDebugUnitTest assembleDebug

mkdir -p dist
apk="dist/$name.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$apk"
sha=$(shasum -a 256 "$apk" | cut -c1-64)
commit=$(git rev-parse HEAD)
echo "$commit" > "dist/$name.commit"

cat > "dist/$name-notes.md" <<EOF
## 変わったこと
$changes

## 入れ方

1. \`$name.apk\` をスマホに送り、ファイルアプリから開いてインストールする（提供元不明のアプリの許可が要る）
2. 公式アプリ（SABERA）を Android の設定 → アプリから強制停止する
3. このアプリを開いて同意し、「グラスを選んでつなぐ」でグラスを選ぶ

## 注意

- 座るか立ち止まって遊ぶ。歩きながら・運転しながらは遊ばない
- デバッグ鍵で署名している。前の版と同じ鍵でビルドしていれば、上書きでインストールできる

SHA-256: \`$sha\`
commit: \`$commit\`
EOF

# macOS の bash 3.2 は、変数の直後の全角文字を変数名の一部と読むので、${} で囲む
echo "版: ${version}（tag ${tag}）"
echo "APK: ${apk}"
echo "SHA-256: ${sha}"
echo "commit: ${commit}"
echo "リリースノート: dist/${name}-notes.md"
