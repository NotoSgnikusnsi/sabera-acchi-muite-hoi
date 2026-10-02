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

## 変更の決まり

- グラスとの通信は SDK の公開 API だけで行う。SDK を通さない BLE の送受信をしない。ファームウェアに触れない
- SDK の aar を展開・逆コンパイルしない。API は SDK の docs・IDE の補完・公式サンプルで調べる
- SDK・PAT・署名鍵を repo に入れない。`.gitignore` に `*.aar`・`*.jks`・`*.keystore` を書き、git の管理から除いている
- INTERNET 権限を足さない。頭の向きを保存しない
- 拍・判定・タッチの挙動を変えたら `GameEngineTest`・`DirectionTest` を直すか足す
- 利用条件・プライバシーポリシー・安全の注意の内容を変えたら、文書の版と `Legal.kt` の `CONSENT_VERSION` を上げる（上げると、次に開いたときに同意の画面をもう一度出す）
- 直下の `PRIVACY.md`・`TERMS.md`・`NOTICE`・`LICENSES/` はビルドで assets に写す。動かすなら `app/build.gradle.kts` の `copyLegalAssets` も直す
- 設定のキー（`SettingsCodec`）は変えない。足すときは、無い値を既定にする
