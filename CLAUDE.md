# CLAUDE.md

スマートグラス SABERA で、あっち向いてホイを遊ぶ Android アプリ（Kotlin・Compose）。Sabera App SDK の公開 API だけでグラスと通信する。

## 構成

- `app/` アプリ（コードは `app/src/main/kotlin/dev/acchimuitehoi/`、テストは `app/src/test/`）
- `docs/` 設計・開発・テスト
- 直下の `PRIVACY.md`・`TERMS.md`・`NOTICE`・`LICENSE`・`LICENSES/` は、ビルドのときにアプリの assets にコピーされる

## 守ること

- SDK の aar を展開・逆コンパイル・`javap` で読まない。公開 API は SDK の docs・IDE の補完・公式サンプルで知る
- ファームウェアに触れない。SDK を通さない BLE の送受信をしない。グラス本体の設定を書き換えない
- PAT・認証情報を読まない・書かない。SDK を repo に入れない
- INTERNET 権限を足さない。頭の向きを保存しない
- repo には公開してよい内容だけを書く（個人の端末名・ホスト名・ホーム配下のパス・メールアドレス・私的な記録への参照を書かない）
- 挙動を変えたらテストを足す。変更の決まりは [docs/development.md](docs/development.md#変更の決まり)
- 利用者に見える文言（画面・`PRIVACY.md`・`TERMS.md`）に、API 名や内部向けの括弧書きを入れない。`NOTICE` の「元にした部分」には、Apache License 2.0 が求める変更したファイルの表示としてファイル名とクラス名を書く

## ドキュメントの書き方

読み手は、書いた人の説明を聞けない人（数日後の自分・別の開発者・初めて読む人）と考えて書く。

1. 言いたいことを最初の文に書く。否定や留保を先に置かない
2. 比喩の動詞や抽象語を使わず、誰が・何を・どうするかを書く
3. 短い文の連打・対句・体言止めの連続を使わない

## どこに書くか

| 書くこと | 置き場所 |
|---|---|
| 使い方の入口・必要なもの・最短の手順 | `README.md` |
| 版ごとの変更 | `CHANGELOG.md` |
| ゲームの進み方・判定・表示 | `docs/design.md` |
| ビルド・SDK の取得・変更の決まり | `docs/development.md` |
| テストと実機での確認 | `docs/testing.md` |
| 版の上げ方・APK の配り方 | `docs/release.md`（ビルドは `scripts/release.sh`） |
| 情報の扱い・利用条件・ライセンス表示 | `PRIVACY.md`・`TERMS.md`・`NOTICE`・`LICENSE`（この repo の Apache License 2.0）・`LICENSES/`（SDK に含まれる Opus） |
