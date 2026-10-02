# リリース

版ごとに tag `vx.y.z` を打ち、GitHub Release に APK を付けて配る。APK は commit と同じ中身からビルドし、リリースノートに SHA-256 と commit を書く。

## 手順

1. `app/build.gradle.kts` の `versionCode` を 1 上げ、`versionName` を新しい版にする
2. `CHANGELOG.md` に `## [x.y.z] - YYYY-MM-DD` の節を足し、変わったことを書く
3. 1 と 2 を commit する
4. APK をビルドできる機械（Mac か x86_64 の Linux）で、その commit を checkout して `scripts/release.sh` を走らせる。
   ユニットテストの後に APK をビルドし、`dist/` に次の 3 つを作る

   | ファイル | 中身 |
   |---|---|
   | `sabera-acchi-muite-hoi-x.y.z.apk` | 配る APK |
   | `sabera-acchi-muite-hoi-x.y.z.commit` | ビルドした commit |
   | `sabera-acchi-muite-hoi-x.y.z-notes.md` | リリースノート（CHANGELOG の節・入れ方・注意・SHA-256・commit） |

5. その commit を GitHub に push してから、Release を作る。`--target` には `dist/*.commit` の commit を渡す

   ```sh
   gh release create vx.y.z dist/sabera-acchi-muite-hoi-x.y.z.apk \
     --target <commit> --title "x.y.z" --notes-file dist/sabera-acchi-muite-hoi-x.y.z-notes.md
   ```

6. `gh release view vx.y.z` で、APK が付いていて下書きになっていないことを確かめる

## 決まり

- APK の名前は `sabera-acchi-muite-hoi-x.y.z.apk`、tag は `vx.y.z`、Release の題は `x.y.z`
- 一度出した版の APK は差し替えない。直すときは版を上げて出し直す
- `dist/` は git の管理から除いている
- 署名はデバッグ鍵。上書きでインストールするには、前の版と同じ機械のデバッグ鍵でビルドする
