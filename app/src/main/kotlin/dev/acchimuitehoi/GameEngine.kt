package dev.acchimuitehoi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

/** グラスのツルのタッチ（SDK の GestureType を、テストできるようにアプリ側の型に写したもの） */
enum class TouchGesture { SINGLE_TAP, DOUBLE_TAP, HOLD }

/** グラスの真ん中に出すもの */
sealed interface GlassMain {
    /** 大きな文字（1〜2 行） */
    data class Text(val lines: List<String>) : GlassMain {
        constructor(vararg lines: String) : this(lines.toList())
    }

    /** 矢印 */
    data class Arrow(val direction: Direction) : GlassMain
}

/** グラスに出す 1 画面。[status] は下の小さな 1 行（null なら出さない） */
data class GlassScreen(val main: GlassMain, val status: String? = null)

/** グラスへの送り口（SDK の CommandManager を包んだ実物と、テストの記録係） */
interface GlassPort {
    /** キャンバスを開いて [screen] を出す（前と変わった部分だけ送る） */
    fun show(screen: GlassScreen)

    /** キャンバスを閉じて、グラス本体のホーム画面に戻す */
    fun close()

    /** 接続が切れたので、送った画面の記録を捨てる（グラスには何も送らない）。次の [show] でキャンバスを開き直す */
    fun forget()

    /** これから出す画面を先に描いておく（拍の時刻に描く分の遅れを無くす） */
    fun prepare(contents: List<GlassMain>) {}
}

/**
 * ゲームの段階。
 * - STANDBY: グラスには何も出さない（本体のホーム画面のまま）
 * - TITLE: タイトルと操作の案内を出して、スタートを待つ
 * - COUNTDOWN / PLAYING: 拍を刻んでいる
 * - GAME_OVER: 結果を出して、もう一度を待つ
 */
enum class Phase { STANDBY, TITLE, COUNTDOWN, PLAYING, GAME_OVER }

/** ゲームが終わった理由 */
enum class EndReason {
    /** 矢印と同じ向きを向いた */
    CAUGHT,

    /** フライングか遅れが続いた（頭の向きのデータが届かない回を含む） */
    FOULS,

    /** 頭の向きのデータが届かない回が続いた */
    NO_SENSOR,
}

/** スマホの画面に出すゲームの様子 */
data class GameState(
    val connected: Boolean = false,
    val phase: Phase = Phase.STANDBY,
    val screen: GlassScreen? = null,
    val streak: Int = 0,
    val beatMs: Long = 0,
    val lastTarget: Direction? = null,
    val lastJudgement: Judgement? = null,
    val endReason: EndReason? = null,
)

/**
 * あっち向いてホイを進める。1 回は 4 拍で、「あっち」「むいて」「矢印（ホイ）」「判定」を 1 拍ずつ出す。
 * 音を出せないので、文字が拍ごとに切り替わることでリズムを伝える。拍の時刻は始めた時刻からの絶対時刻で決め、
 * 送信や描画が遅れても拍がずれていかないようにする。
 *
 * 判定は [judge]。矢印の向きは、その回の頭の向きを見る前に乱数で決める。
 * タッチは、ダブルタップで先へ進み（タイトルを出す・始める・もう一度）、長押しで 1 つ戻る（やめてタイトルへ・ホームへ）。
 *
 * 時刻は [now]（単調な時計）で数える。メインスレッドだけから呼ぶ。
 */
class GameEngine(
    private val port: GlassPort,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val settings: () -> AppSettings,
    private val onScore: (Tempo, Int) -> Unit,
    private val random: Random = Random.Default,
    private val onState: (GameState) -> Unit = {},
) {
    var state = GameState()
        private set

    private var gameJob: Job? = null
    private var idleJob: Job? = null
    private val samples = ArrayDeque<PoseSample>()

    fun onConnected() {
        if (state.connected) return
        update { it.copy(connected = true) }
        showTitle()
    }

    /** 切れたら何も送らずに止める（切断中の送信は捨てられる） */
    fun onDisconnected() {
        cancelTimers()
        samples.clear()
        port.forget()
        update { GameState() }
    }

    fun onPose(yaw: Float, pitch: Float) {
        val t = now()
        samples.addLast(PoseSample(t, yaw, pitch))
        while (samples.isNotEmpty() && samples.first().timeMs < t - KEEP_MS) samples.removeFirst()
    }

    fun onGesture(g: TouchGesture) {
        when (state.phase) {
            Phase.STANDBY -> if (g == TouchGesture.DOUBLE_TAP) showTitle()
            Phase.TITLE -> when (g) {
                TouchGesture.DOUBLE_TAP -> start()
                TouchGesture.HOLD -> standby()
                TouchGesture.SINGLE_TAP -> Unit
            }
            Phase.COUNTDOWN, Phase.PLAYING -> if (g == TouchGesture.HOLD) showTitle()
            Phase.GAME_OVER -> when (g) {
                TouchGesture.DOUBLE_TAP -> start()
                TouchGesture.HOLD -> showTitle()
                TouchGesture.SINGLE_TAP -> Unit
            }
        }
    }

    /** カウントダウンから始める（どの段階からでも） */
    fun start() {
        if (!state.connected) return
        cancelTimers()
        port.prepare(PREPARED)
        gameJob = scope.launch {
            try {
                runGame()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 想定外の例外で拍が止まったまま「遊んでいる」にならないよう、タイトルに戻す
                runCatching { showTitle() }
            }
        }
    }

    fun showTitle() {
        if (!state.connected) return
        cancelTimers()
        show(TITLE_SCREEN)
        update { it.copy(phase = Phase.TITLE, streak = 0, endReason = null) }
        startIdleTimer()
    }

    /** グラスの表示を本体のホーム画面に戻す */
    fun standby() {
        if (!state.connected) return
        cancelTimers()
        port.close()
        update { it.copy(phase = Phase.STANDBY, screen = null) }
    }

    /** 接続をやめるときの後片付け。出していた画面を閉じる */
    fun release() {
        val wasShowing = state.connected && state.phase != Phase.STANDBY
        cancelTimers()
        samples.clear()
        if (wasShowing) port.close()
        update { GameState() }
    }

    private suspend fun runGame() {
        val tempo = settings().tempo
        var beat = tempo.startMs
        var streak = 0
        var fouls = 0
        var t = now()
        update { it.copy(phase = Phase.COUNTDOWN, streak = 0, beatMs = beat, lastTarget = null, lastJudgement = null, endReason = null) }
        for (n in COUNTDOWN_FROM downTo 1) {
            show(GlassScreen(GlassMain.Text("$n"), COUNTDOWN_HINT))
            t += beat
            sleepUntil(t)
        }
        update { it.copy(phase = Phase.PLAYING) }
        while (true) {
            // 矢印の向きは、この回の頭の向きを見る前に決める
            val target = Direction.entries[random.nextInt(Direction.entries.size)]
            val roundStart = t
            val status = streakText(streak)
            show(GlassScreen(GlassMain.Text(WORD_ACCHI), status))
            t += beat
            sleepUntil(t)
            show(GlassScreen(GlassMain.Text(WORD_MUITE), status))
            t += beat
            sleepUntil(t)
            val hoi = t
            show(GlassScreen(GlassMain.Arrow(target), status))
            update { it.copy(lastTarget = target) }
            t += beat
            sleepUntil(t)

            val timing = RoundTiming(
                frontFrom = roundStart + beat / 2,
                watchFrom = roundStart + beat,
                hoiMs = hoi,
                earlyMs = EARLY_MS,
                deadlineMs = hoi + beat - RESULT_MARGIN_MS,
            )
            val j = judge(samples.toList(), timing, settings().axes)
            update { it.copy(lastJudgement = j) }
            when (j) {
                is Judgement.Turned -> if (j.direction == target) {
                    show(GlassScreen(GlassMain.Text(WORD_OUT), status))
                    t += beat
                    sleepUntil(t)
                    endGame(EndReason.CAUGHT, tempo, streak)
                    return
                } else {
                    streak++
                    fouls = 0
                    show(GlassScreen(GlassMain.Text(WORD_SAFE), streakText(streak)))
                }
                Judgement.Early -> {
                    fouls++
                    show(GlassScreen(GlassMain.Text(WORD_EARLY), FOUL_HINT))
                }
                Judgement.Late -> {
                    fouls++
                    show(GlassScreen(GlassMain.Text(WORD_LATE), FOUL_HINT))
                }
                // BLE の途切れで 1 回届かないこともあるので、すぐには終えずにフライング・遅れと同じく数える
                Judgement.NoSensor -> {
                    fouls++
                    show(GlassScreen(GlassMain.Text(WORD_NO_SENSOR), FOUL_HINT))
                }
            }
            update { it.copy(streak = streak) }
            t += beat
            sleepUntil(t)
            if (fouls >= FOUL_LIMIT) {
                endGame(if (j == Judgement.NoSensor) EndReason.NO_SENSOR else EndReason.FOULS, tempo, streak)
                return
            }
            if (j is Judgement.Turned) beat = max(tempo.fastestMs, beat - STEP_MS)
            update { it.copy(beatMs = beat) }
        }
    }

    private fun endGame(reason: EndReason, tempo: Tempo, streak: Int) {
        val before = settings().bestFor(tempo)
        val newBest = streak > before
        if (newBest) onScore(tempo, streak)
        val main = when {
            reason == EndReason.NO_SENSOR -> GlassMain.Text("向きが", "とれない")
            newBest -> GlassMain.Text("れんぞく $streak", "ベスト更新!")
            else -> GlassMain.Text("れんぞく $streak", "ベスト $before")
        }
        show(GlassScreen(main, GAME_OVER_HINT))
        update { it.copy(phase = Phase.GAME_OVER, streak = streak, endReason = reason) }
        startIdleTimer()
    }

    private suspend fun sleepUntil(t: Long) {
        val d = t - now()
        if (d > 0) delay(d)
    }

    /** タイトルや結果を出したまま放っておかれたら、本体のホーム画面に戻す */
    private fun startIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            idleJob = null
            standby()
        }
    }

    private fun cancelTimers() {
        gameJob?.cancel()
        gameJob = null
        idleJob?.cancel()
        idleJob = null
    }

    private fun show(screen: GlassScreen) {
        port.show(screen)
        update { it.copy(screen = screen) }
    }

    private inline fun update(f: (GameState) -> GameState) {
        state = f(state)
        onState(state)
    }

    companion object {
        /** セーフ 1 回ごとに 1 拍を短くする長さ */
        const val STEP_MS = 15L

        /** 「ホイ」の拍を送るより、これだけ前に向いたらフライング */
        const val EARLY_MS = 250L

        /** 判定の締め切りは、判定の拍の手前のこの長さ（判定を描いて送る分を残す） */
        const val RESULT_MARGIN_MS = 80L

        /** フライングか遅れがこの回数続いたら終わる（グラスを外して放っておかれても続けない） */
        const val FOUL_LIMIT = 3

        const val COUNTDOWN_FROM = 3

        /** タイトルや結果をこの長さ放っておかれたら、ホーム画面に戻す */
        const val IDLE_MS = 60_000L

        /** 判定のために持っておくサンプルの長さ（1 回の 4 拍より長く） */
        const val KEEP_MS = 6_000L

        const val WORD_ACCHI = "あっち"
        const val WORD_MUITE = "むいて"
        const val WORD_OUT = "アウト!"
        const val WORD_SAFE = "セーフ"
        const val WORD_EARLY = "はやい!"
        const val WORD_LATE = "おそい!"
        const val WORD_NO_SENSOR = "とれない"
        const val COUNTDOWN_HINT = "長押しでやめる"
        const val FOUL_HINT = "もう一回"
        const val GAME_OVER_HINT = "2回タップでもう一度"

        /** 遊ぶ前に描いておく画面（拍ごとに出す言葉と 4 つの矢印） */
        val PREPARED: List<GlassMain> = listOf("3", "2", "1", WORD_ACCHI, WORD_MUITE, WORD_SAFE).map { GlassMain.Text(it) } +
            Direction.entries.map { GlassMain.Arrow(it) }

        val TITLE_SCREEN = GlassScreen(GlassMain.Text("あっち向いて", "ホイ"), "2回タップでスタート")

        fun streakText(streak: Int) = "れんぞく $streak"
    }
}
