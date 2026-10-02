package dev.acchimuitehoi

import android.content.Context

/** [AppSettings] を SharedPreferences に保存する。キーと値の対応は [SettingsCodec] */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("acchi_muite_hoi", Context.MODE_PRIVATE)

    fun load(): AppSettings = SettingsCodec.decode(prefs.all)

    fun save(s: AppSettings) {
        val editor = prefs.edit()
        SettingsCodec.encode(s).forEach { (key, value) ->
            when (value) {
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}

/** 保存の形。Android に依らないのでユニットテストで確かめる */
object SettingsCodec {
    private const val TEMPO = "tempo"
    private const val FLIP_LR = "flip_left_right"
    private const val FLIP_UD = "flip_up_down"
    private const val CONSENT = "consent_version"
    private fun bestKey(t: Tempo) = "best_${t.name.lowercase()}"

    fun encode(s: AppSettings): Map<String, Any> = buildMap {
        put(TEMPO, s.tempo.name)
        put(FLIP_LR, s.axes.flipLeftRight)
        put(FLIP_UD, s.axes.flipUpDown)
        put(CONSENT, s.consentVersion)
        Tempo.entries.forEach { put(bestKey(it), s.bestFor(it)) }
    }

    /** 無い値・型の違う値・知らない値は既定にする（落とさない） */
    fun decode(stored: Map<String, *>): AppSettings {
        val d = AppSettings()
        return AppSettings(
            tempo = (stored[TEMPO] as? String)?.let { name -> Tempo.entries.firstOrNull { it.name == name } } ?: d.tempo,
            axes = Axes(
                flipLeftRight = stored[FLIP_LR] as? Boolean ?: d.axes.flipLeftRight,
                flipUpDown = stored[FLIP_UD] as? Boolean ?: d.axes.flipUpDown,
            ),
            best = Tempo.entries.associateWith { ((stored[bestKey(it)] as? Int) ?: 0).coerceAtLeast(0) }.filterValues { it > 0 },
            consentVersion = (stored[CONSENT] as? Int ?: d.consentVersion).coerceAtLeast(0),
        )
    }
}
