# あっち向いてホイ（SABERA 対応）

スマートグラス SABERA で、あっち向いてホイを 1 人で遊ぶ Android アプリ。グラスは音を出せないので、グラスの文字が 1 拍ずつ切り替わることでリズムを伝える。

## 安全の注意

座るか立ち止まって遊ぶ。歩きながら・運転しながらは遊ばない。顔を向ける前に、まわりに人や物が無いことを確かめる。全文は [TERMS.md](TERMS.md)。

## 遊び方

1. グラスに「あっち」「むいて」が 1 拍ずつ出て、次の拍で矢印が出る（この拍が「ホイ」）
2. 矢印が出る拍に合わせて、上・下・左・右のどれかへ顔を向ける
3. 矢印と同じ向きならアウトで終わり、違う向きならセーフで次の回へ進む。セーフが続くと拍が速くなる
4. 矢印が出る 0.25 秒より前に向くと「はやい」、次の拍までに向かないと「おそい」になる。グラスから頭の向きが届かなかった回は「とれない」と出る。どれもセーフには数えずに次の回へ進み、3 回つづくと終わる

グラスのツルの操作:

| 操作 | タイトル | 遊んでいる間 | 結果 | ホーム画面（何も出していない） |
|---|---|---|---|---|
| 2 回タップ | スタート | — | もう一度 | タイトルを出す |
| 長押し | ホーム画面に戻る | やめてタイトルへ | タイトルへ | — |

スマホの画面では、グラスへの接続・スタートとやめる・テンポ（ゆっくり・ふつう・はやい）の選択と、最高記録を見られる。顔の向きが逆に判定されるときは「向きの確認と調整」で左右・上下を入れ替える。

## 必要なもの

- SABERA（ファームウェア 1.2.0 以上。キャンバスと IMU を使う）
- Android 12 以上のスマホ
- 公式アプリ（SABERA）を使っていたら、遊ぶ前に Android の設定 → アプリから強制停止しておく

## ビルド

Sabera App SDK は GitHub Packages の private なパッケージで、この repo には入れない。`read:packages` だけの classic PAT を `~/.gradle/gradle.properties` に書いてからビルドする。手順は [docs/development.md](docs/development.md)。

```sh
./gradlew testDebugUnitTest assembleDebug
```

## ドキュメント

| 文書 | 内容 |
|---|---|
| [docs/design.md](docs/design.md) | ゲームの進み方・判定・グラスの表示の決め方 |
| [docs/development.md](docs/development.md) | ビルド・SDK の取得・変更の決まり |
| [docs/testing.md](docs/testing.md) | ユニットテストと実機での確認項目 |
| [docs/release.md](docs/release.md) | 版の上げ方と、tag と GitHub Release で APK を配る手順 |
| [CHANGELOG.md](CHANGELOG.md) | 版ごとの変更 |
| [PRIVACY.md](PRIVACY.md)・[TERMS.md](TERMS.md) | 情報の扱い・利用条件と安全の注意 |

## ライセンス

公式サンプル（Apache License 2.0）から写した部分と、その表示は [NOTICE](NOTICE) と [LICENSES/](LICENSES/)。Sabera App SDK 本体はこの repo のライセンスの対象外で、SDK の利用規約に従う。「SABERA」の名称は、このアプリが SABERA に対応していることを示すためだけに使っている。
