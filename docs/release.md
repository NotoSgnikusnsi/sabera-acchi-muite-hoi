# リリース

版ごとに tag `vx.y.z` を打ち、GitHub Release に APK を付けて配る。APK は commit と同じ中身からビルドし、リリースノートに SHA-256 と commit を書く。

## 手順

1. `app/build.gradle.kts` の `versionCode` を前の版より 1 上げ、`versionName` を新しい版にする。`versionCode` が増えていないと、前の版の上から入れられない（スクリプトはこれを確かめない）
2. `CHANGELOG.md` に `## [x.y.z] - YYYY-MM-DD` の節を足し、変わったことを書く
3. `versionCode`・`versionName` の変更と CHANGELOG の節を 1 つの commit にする
4. APK をビルドできる機械（Mac か x86_64 の Linux）で、手順 3 の commit を checkout し、`git fetch --tags` してから `scripts/release.sh` を実行する。
   スクリプトは、commit していない変更（追跡していないファイルを含む）があるか、その版の tag が既にあると止まる。tag を fetch していないと、GitHub にある tag を見落とす。
   スクリプトはユニットテストの後に APK をビルドし、`dist/` に次の 3 つを作る

   | ファイル | 中身 |
   |---|---|
   | `sabera-acchi-muite-hoi-x.y.z.apk` | 配る APK |
   | `sabera-acchi-muite-hoi-x.y.z.commit` | ビルドした commit |
   | `sabera-acchi-muite-hoi-x.y.z-notes.md` | リリースノート（CHANGELOG の節・入れ方・注意・SHA-256・commit） |

5. 手順 4 でビルドした commit（`dist/*.commit` に書いてある）を GitHub に push してから、Release を作る。
   tag `vx.y.z` が GitHub に既にあると、`gh` はその tag を使って `--target` を無視するので、手順 4 の `git fetch --tags` で無いことを確かめておく

   ```sh
   gh release create vx.y.z dist/sabera-acchi-muite-hoi-x.y.z.apk \
     --target "$(cat dist/sabera-acchi-muite-hoi-x.y.z.commit)" --title "x.y.z" \
     --notes-file dist/sabera-acchi-muite-hoi-x.y.z-notes.md
   ```

6. 出した Release を確かめる
   - `gh release view vx.y.z` で、APK が付いていて下書きになっていない
   - `git ls-remote origin refs/tags/vx.y.z` の commit が `dist/*.commit` と同じ
   - `gh release download vx.y.z -p '*.apk' -D <空のディレクトリ>` で落とした APK の `shasum -a 256` が、リリースノートの SHA-256 と同じ

## 決まり

- APK の名前は `sabera-acchi-muite-hoi-x.y.z.apk`、tag は `vx.y.z`、Release の題は `x.y.z`
- 一度出した版の APK は差し替えない。直すときは版を上げて出し直す
- `dist/` は `.gitignore` に書き、git の管理から除いている
- CHANGELOG の末尾にリンク定義（`[x.y.z]: URL`）を置いても、リリースノートには入らない
- 署名はデバッグ鍵。上書きでインストールするには、前の版と同じ機械のデバッグ鍵でビルドする
