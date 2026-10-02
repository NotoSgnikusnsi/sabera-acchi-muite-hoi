package dev.acchimuitehoi

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library

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
    SLF4J("SLF4J API Module の MIT License", "legal/LICENSES/SLF4J-MIT.txt"),
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

/**
 * APK に入る依存ライブラリの一覧。ライブラリごとに名前・版・ライセンス・作者を出す。
 * ライセンスの全文は「ライセンス」画面の別の節に出す（Apache License 2.0 は LegalDoc.APACHE、MIT License は LegalDoc.SLF4J）
 */
fun formatLibraries(libraries: List<Library>): String {
    val lines = libraries.map { lib ->
        val authors = (listOfNotNull(lib.organization?.name) + lib.developers.mapNotNull { it.name })
            .filter { it.isNotBlank() }
            .distinct()
        buildString {
            append(lib.name)
            lib.artifactVersion?.let { append(" ").append(it) }
            append("\n  ").append(lib.licenses.joinToString("・") { it.name }.ifEmpty { "ライセンスの記載なし" })
            if (authors.isNotEmpty()) append("\n  作者: ").append(authors.joinToString("・"))
            append("\n  ").append(lib.uniqueId)
        }
    }
    return "このアプリには次の ${libraries.size} 個のライブラリが入っている。\n\n" + lines.joinToString("\n\n")
}

/** ビルドのときに AboutLibraries が書き出す res/raw/aboutlibraries.json を読む。Libs.Builder がライブラリを名前の順に並べる */
fun readLibraries(context: Context): String =
    runCatching {
        val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        formatLibraries(Libs.Builder().withJson(json).build().libraries)
    }.getOrElse { "ライブラリの一覧を読み込めなかった。" }

/** 同意の画面と設定の画面に出す安全の注意 */
val SAFETY_POINTS = listOf(
    "座るか立ち止まって遊ぶ。歩きながら・運転しながら・乗り物を操作しながら遊ばない",
    "まわりに人や物が無いことを確かめてから、顔を向ける。首を強く・速く振りすぎない",
    "首や目が疲れたら、すぐにやめて休む。表示の切り替わりがつらいときは、テンポを「ゆっくり」にするか、やめる",
)
