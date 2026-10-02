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

## 公開前の点検

GitHub Release で APK を配る前に、外に出してよいかを次の 8 項目で確かめる。集める情報・依存・外部サービスを変えたら、配る前に表を見直す。最後に見直したのは 0.1.2（2026-10-03）。

| 項目 | 判定 | 根拠 |
|---|---|---|
| 集めるデータと送信先 | 済 | INTERNET 権限が無く、外部へは何も送らない。頭の向きとツルのタッチはメモリで扱うだけで保存しない。端末に保存するのは設定と記録（`SettingsStore`）と前回つないだグラスの識別子（`AcchiApplication` の `SharedPrefsDevicePersistence`）だけで、`data_extraction_rules.xml` でバックアップと機種変更の移行から外している |
| プライバシーポリシー | 済 | `PRIVACY.md` の表が、上の棚卸しと同じ項目・保存先を書いている。アプリの初回の同意とメインの画面から読める。SDK 利用規約の第6条3項が求める公表は、`PRIVACY.md` を repo に置いて行う |
| 利用規約 | 済 | `TERMS.md` に、提供者・無保証・安全の注意を書いている。投稿・アカウント・課金・利用者どうしの交流は無い。SDK 利用規約の第6条4項が求める利用条件（提供者が開発者で、責任も開発者にあること）として置いている |
| 外部 API / SDK の規約 | 済 | 使う外部のものは Sabera App SDK だけ。第3条1項が SDK を組み込んだアプリの配布を許し、第4条に当たる使い方（SDK の単体での再配布・解析・SDK を通さないグラスの制御・ファームウェアの変更）はしていない。第5条1項の第三者ソフトウェアの表示は「ライセンス」画面で出し、第6条5項の安全の注意は同意の画面と設定の画面に出している |
| OSS ライセンス表示 | 済 | 依存の一覧を AboutLibraries で作って「ライセンス」画面に出す。依存のライセンスは Apache License 2.0 と、SLF4J API Module の MIT License だけで、GPL 系は無い。SDK に含まれる Opus（BSD 3-Clause）と SLF4J API Module の著作権表示は `LICENSES/` から出す。0.1.2 の依存の jar と aar に NOTICE ファイルは無かった |
| フォント / アイコン / 画像 | 済 | フォントは端末の標準のものだけを使う。ランチャーアイコンは、この repo で描いた指さしの手のドット絵（`app/src/main/res/` の 5 ファイル）で、他から持ってきた素材は無い。グラスに出す文字と矢印はアプリが描く |
| 法令の該当性 | 不要 | 個人情報を取得せず、外部へ送信しないので、個人情報保護法の利用目的の通知と、電気通信事業法の外部送信規律に当たらない。無償で課金もアプリ内通貨も無いので、特定商取引法と資金決済法にも当たらない。ストアに出さないので、ファミリー向けポリシーの対象にならない |
| 名称・表示 | [人手] | アプリ名は「あっち向いてホイ」で、遊びの一般的な名前を使っている。SABERA のロゴは使わず、同意の画面に SABERA の提供元の製品ではないことを書いている。SDK 利用規約の第5条2項は、SABERA の名称の使用を「本デバイスに対応することを事実として示す範囲」に限る。repo 名と APK の名前の `sabera`、NOTICE の「SABERA 対応」がこの範囲に入るかは、規約に明記が無いので提供者が判断する |
