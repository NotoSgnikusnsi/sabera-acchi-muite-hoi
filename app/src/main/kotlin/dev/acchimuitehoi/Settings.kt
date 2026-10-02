package dev.acchimuitehoi

/**
 * 拍の速さ。1 拍の長さ [startMs] から始め、セーフになるたびに [GameEngine.STEP_MS] ずつ短くし、[fastestMs] で止める。
 */
enum class Tempo(val label: String, val startMs: Long, val fastestMs: Long) {
    SLOW("ゆっくり", 800, 560),
    NORMAL("ふつう", 650, 460),
    FAST("はやい", 540, 400),
}

/** アプリの設定と記録。端末の中だけに保存する（[SettingsStore]） */
data class AppSettings(
    val tempo: Tempo = Tempo.NORMAL,
    val axes: Axes = Axes(),
    /** テンポごとの最高の連続セーフ数 */
    val best: Map<Tempo, Int> = emptyMap(),
    /** 同意した利用条件の版（0 = まだ） */
    val consentVersion: Int = 0,
) {
    fun bestFor(tempo: Tempo): Int = best[tempo] ?: 0

    /** 記録を更新する（今の最高より大きいときだけ） */
    fun withScore(tempo: Tempo, streak: Int): AppSettings =
        if (streak > bestFor(tempo)) copy(best = best + (tempo to streak)) else this
}
