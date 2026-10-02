package dev.acchimuitehoi

import android.content.Context

/**
 * 人に渡すときに要る表示と同意。文面は repo の直下の PRIVACY.md・TERMS.md・NOTICE・LICENSE・LICENSES/ で、
 * ビルドのときに assets/legal/ に写したものを読む（app/build.gradle.kts の copyLegalAssets）。
 */

/** 初回の同意の文面の版。安全の注意・利用条件・プライバシーポリシーのどれかを変えたら上げる（上げると次に開いたときにもう一度出す）。ライセンスの表示は同意の対象ではないので、変えても上げない */
const val CONSENT_VERSION = 1

enum class LegalDoc(val title: String, val asset: String) {
    TERMS("利用条件と安全の注意", "legal/TERMS.md"),
    PRIVACY("プライバシーポリシー", "legal/PRIVACY.md"),
    NOTICE("このアプリの表示（NOTICE）", "legal/NOTICE"),
    APACHE("Apache License 2.0", "legal/LICENSE"),
    OPUS("Opus（Sabera App SDK に含まれる）", "legal/LICENSES/Opus-BSD-3-Clause.txt"),
}

fun AppSettings.needsConsent(): Boolean = consentVersion < CONSENT_VERSION

fun AppSettings.acceptConsent(): AppSettings = copy(consentVersion = CONSENT_VERSION)

fun readLegal(context: Context, doc: LegalDoc): String =
    runCatching { context.assets.open(doc.asset).bufferedReader().use { it.readText() } }
        .getOrElse { "この文書を読み込めなかった。アプリを入れ直してから、もう一度開く。" }

/** SDK に入っている Opus の全文（SDK の docs が案内する場所）。アプリの classpath のリソースとして読む（aar は展開しない） */
const val SDK_OPUS_NOTICE = "META-INF/third-party-notices/opus-LICENSE.txt"

/** Opus の著作権表示と免責。SDK が全文を入れていればそれを使い、無ければアプリに同梱した全文を使う */
fun readOpusNotice(context: Context): String {
    val fromSdk = runCatching {
        LegalDoc::class.java.classLoader?.getResourceAsStream(SDK_OPUS_NOTICE)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
    return if (!fromSdk.isNullOrBlank()) fromSdk else readLegal(context, LegalDoc.OPUS)
}

/** 同意の画面と設定の画面に出す安全の注意 */
val SAFETY_POINTS = listOf(
    "座るか立ち止まって遊ぶ。歩きながら・運転しながら・乗り物を操作しながら遊ばない",
    "まわりに人や物が無いことを確かめてから、顔を向ける。首を強く・速く振りすぎない",
    "首や目が疲れたら、すぐにやめて休む。表示の切り替わりがつらいときは、テンポを「ゆっくり」にするか、やめる",
)
