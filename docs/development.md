# 開発

## SDK の取得

Sabera App SDK（`jp.jig.sabera.app.sdk:sabera-app-core`）は、`jig-SABERA/sabera-sdk-packages` の GitHub Packages にある private なパッケージ。

1. GitHub で classic PAT を作る。スコープは `read:packages` だけにする（GitHub Packages は fine-grained PAT を受け付けない）
2. `~/.gradle/gradle.properties` に書く（repo の中の `gradle.properties` には書かない）

```properties
GitHubPackagesUsername=<GitHub のユーザー名>
GitHubPackagesPassword=<PAT>
```

## ビルドとテスト

```sh
./gradlew testDebugUnitTest   # ユニットテスト
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

- JDK 17 以上が要る
- Android SDK の場所は `local.properties` の `sdk.dir`（repo に入れない）
- AGP の aapt2 には linux-aarch64 版が無いので、arm64 の Linux では APK を作れない。Mac か x86_64 の Linux でビルドする
- 版は `app/build.gradle.kts` の `versionCode` と `versionName`。配る手順は [release.md](release.md)

## ランチャーアイコン

ランチャーアイコンは、指さしの手のドット絵のアダプティブアイコンで、`app/src/main/res/` の次の 5 ファイルでできている。

| ファイル | 中身 |
|---|---|
| `drawable/ic_launcher_foreground.xml` | 前景。手の輪郭（`#1F2203`）と中（`#C9D7FD`）の 2 つの path |
| `drawable/ic_launcher_monochrome.xml` | Android 13 以降のテーマアイコン用。前景の中（`#C9D7FD`）の path と同じ形を、白 1 色で描く |
| `values/ic_launcher_background.xml` | 背景色（`#7E8904`） |
| `mipmap-anydpi/ic_launcher.xml`・`ic_launcher_round.xml` | 前景・monochrome・背景色を組み合わせるアダプティブアイコンの定義。2 つの中身は同じ |

- 絵は 108 x 108 の viewport に、1 辺 4.111 の正方形のドットを並べて描いている。前景と monochrome の絵を変えるときは、`pathData` の矩形（`M x y h 幅 v 高さ z`）を足し引きする
- ランチャーはアイコンを円や角丸の形に切り抜く。Android のアダプティブアイコンの安全域は直径 66dp なので、ドットはすべて中心から半径 33 の円の内側に置く
- monochrome の `pathData` は、前景の中（`#C9D7FD`）の path と同じにそろえ、色は白 1 色のままにする。テーマアイコンの色はシステムが壁紙に合わせて付ける
- minSdk が 31 なので、アダプティブアイコンは版の修飾子の無い `mipmap-anydpi/` に置き、密度ごとの PNG は置かない。アダプティブアイコンは Android 8.0（API 26）以上で使われ、この minSdk ではすべての端末が対象になる

## 変更の決まり

- グラスとの通信は SDK の公開 API だけで行う。SDK を通さない BLE の送受信をしない。ファームウェアに触れない
- SDK の aar を展開・逆コンパイルしない。API は SDK の docs・IDE の補完・公式サンプルで調べる
- SDK・PAT・署名鍵を repo に入れない。`.gitignore` に `*.aar`・`*.jks`・`*.keystore` を書き、git の管理から除いている
- INTERNET 権限を足さない。頭の向きを保存しない
- 拍・判定・タッチの挙動を変えたら `GameEngineTest`・`DirectionTest` を直すか足す
- 利用条件・プライバシーポリシー・安全の注意の内容を変えたら、文書の版と `Legal.kt` の `CONSENT_VERSION` を上げる（上げると、次に開いたときに同意の画面をもう一度出す）
- 直下の `PRIVACY.md`・`TERMS.md`・`NOTICE`・`LICENSES/` はビルドで assets に写す。動かすなら `app/build.gradle.kts` の `copyLegalAssets` も直す
- 設定のキー（`SettingsCodec`）は変えない。足すときは、無い値を既定にする
