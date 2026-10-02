package dev.acchimuitehoi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 送った画面と閉じたことを、時刻つきで覚える */
class FakePort(private val now: () -> Long) : GlassPort {
    val events = mutableListOf<Pair<Long, Any>>()

    override fun show(screen: GlassScreen) {
        events += now() to screen
    }

    override fun close() {
        events += now() to CLOSE
    }

    var forgets = 0

    override fun forget() {
        forgets++
    }

    val prepared = mutableListOf<GlassMain>()

    override fun prepare(contents: List<GlassMain>) {
        prepared += contents
    }

    fun screens(): List<Pair<Long, GlassScreen>> = events.mapNotNull { (t, e) -> (e as? GlassScreen)?.let { t to it } }

    fun last(): Any? = events.lastOrNull()?.second

    companion object {
        const val CLOSE = "close"
    }
}

/** 矢印の向きを決まった順で返す（Direction.entries の添字: 0 上・1 下・2 左・3 右） */
class FixedRandom(private vararg val seq: Int) : Random() {
    private var i = 0

    override fun nextBits(bitCount: Int): Int = seq[i++ % seq.size]
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineTest {
    private class Harness(val scope: TestScope, vararg targets: Int, val pose: (Long) -> Pair<Float, Float>?) {
        val now = { scope.testScheduler.currentTime }
        var settings = AppSettings()
        val scores = mutableListOf<Pair<Tempo, Int>>()
        val port = FakePort(now)
        val engine = GameEngine(
            port = port,
            scope = scope.backgroundScope,
            now = now,
            settings = { settings },
            onScore = { t, s ->
                scores += t to s
                settings = settings.withScore(t, s)
            },
            random = FixedRandom(*targets),
        )

        /** 20ms ごと（50Hz）に IMU のサンプルを渡す */
        fun feed() = scope.backgroundScope.launch {
            while (true) {
                pose(now())?.let { (yaw, pitch) -> engine.onPose(yaw, pitch) }
                delay(20)
            }
        }

        fun screenAt(t: Long): GlassScreen? = port.screens().lastOrNull { it.first == t }?.second

        fun mainAt(t: Long): GlassMain? = screenAt(t)?.main
    }

    private fun text(vararg lines: String) = GlassMain.Text(*lines)

    // ふつうのテンポ: 1 拍 650ms。カウントダウン 0・650・1300、1 回目は あっち 1950・むいて 2600・ホイ 3250・判定 3900
    private val hoi1 = 3250L
    private val result1 = 3900L

    private fun front(): Pair<Float, Float> = 0f to 0f

    /** 右を向く（既定の軸では yaw が減ると右） */
    private fun right(): Pair<Float, Float> = -30f to 0f

    @Test
    fun beatsFollowTheTempoFromTheStart() = runTest {
        val h = Harness(this, 0) { front() }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(hoi1 + 1)
        runCurrent()
        val shown = h.port.screens().drop(1).map { it.first to it.second.main }
        assertEquals(
            listOf(
                0L to text("3"),
                650L to text("2"),
                1300L to text("1"),
                1950L to text(GameEngine.WORD_ACCHI),
                2600L to text(GameEngine.WORD_MUITE),
                hoi1 to GlassMain.Arrow(Direction.UP),
            ),
            shown,
        )
    }

    @Test
    fun turningAwayFromTheArrowIsSafeAndSpeedsUp() = runTest {
        // 矢印は上、顔は右
        val h = Harness(this, 0) { t -> if (t in hoi1..hoi1 + 450) right() else front() }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(result1 + 1)
        runCurrent()
        assertEquals(GlassScreen(text(GameEngine.WORD_SAFE), "れんぞく 1"), h.screenAt(result1))
        assertEquals(1, h.engine.state.streak)
        // 次の回は 15ms 短い拍で進む
        val next = result1 + 650
        advanceTimeBy(next + 635 - result1)
        runCurrent()
        assertEquals(text(GameEngine.WORD_ACCHI), h.mainAt(next))
        assertEquals(text(GameEngine.WORD_MUITE), h.mainAt(next + 635))
    }

    @Test
    fun turningWithTheArrowIsOutAndUpdatesTheBest() = runTest {
        // 1 回目は矢印が上で右を向いてセーフ、2 回目は矢印が右で右を向いてアウト
        val round2 = result1 + 650
        val hoi2 = round2 + 2 * 635
        val h = Harness(this, 0, 3) { t -> if (t in hoi1..hoi1 + 450 || t in hoi2..hoi2 + 450) right() else front() }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        val result2 = hoi2 + 635
        advanceTimeBy(result2 + 635 + 1)
        runCurrent()
        assertEquals(text(GameEngine.WORD_OUT), h.mainAt(result2))
        assertEquals(GlassScreen(text("れんぞく 1", "ベスト更新!"), GameEngine.GAME_OVER_HINT), h.screenAt(result2 + 635))
        assertEquals(Phase.GAME_OVER, h.engine.state.phase)
        assertEquals(EndReason.CAUGHT, h.engine.state.endReason)
        assertEquals(listOf(Tempo.NORMAL to 1), h.scores)
    }

    @Test
    fun turningBeforeHoiIsEarlyAndThreeFoulsEndTheGame() = runTest {
        // 毎回「むいて」の途中（ホイの 550ms 前）で右を向く。フライングは拍を速くしないので 1 回は 2600ms
        val h = Harness(this, 0) { t ->
            val inRound = (t - 1950).mod(2600L)
            if (t >= 1950 && inRound in 750L..1200L) right() else front()
        }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(result1 + 1)
        runCurrent()
        assertEquals(GlassScreen(text(GameEngine.WORD_EARLY), GameEngine.FOUL_HINT), h.screenAt(result1))
        val thirdResult = result1 + 2 * 2600
        advanceTimeBy(thirdResult + 650 + 1 - (result1 + 1))
        runCurrent()
        assertEquals(text(GameEngine.WORD_EARLY), h.mainAt(thirdResult))
        assertEquals(Phase.GAME_OVER, h.engine.state.phase)
        assertEquals(EndReason.FOULS, h.engine.state.endReason)
        assertEquals(GlassScreen(text("れんぞく 0", "ベスト 0"), GameEngine.GAME_OVER_HINT), h.screenAt(thirdResult + 650))
    }

    @Test
    fun notTurningIsLate() = runTest {
        val h = Harness(this, 0) { front() }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(result1 + 1)
        runCurrent()
        assertEquals(text(GameEngine.WORD_LATE), h.mainAt(result1))
        assertEquals(Phase.PLAYING, h.engine.state.phase)
    }

    @Test
    fun missingImuIsCountedAsAFoulAndThreeEndTheGame() = runTest {
        val h = Harness(this, 0) { null }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(result1 + 1)
        runCurrent()
        assertEquals(GlassScreen(text(GameEngine.WORD_NO_SENSOR), GameEngine.FOUL_HINT), h.screenAt(result1))
        assertEquals(Phase.PLAYING, h.engine.state.phase)
        val thirdResult = result1 + 2 * 2600
        advanceTimeBy(thirdResult + 650 - result1)
        runCurrent()
        assertEquals(Phase.GAME_OVER, h.engine.state.phase)
        assertEquals(EndReason.NO_SENSOR, h.engine.state.endReason)
    }

    @Test
    fun startDrawsTheBeatScreensAhead() = runTest {
        val h = Harness(this, 0) { front() }
        h.engine.onConnected()
        h.engine.start()
        assertTrue(Direction.entries.all { GlassMain.Arrow(it) in h.port.prepared })
        assertTrue(text(GameEngine.WORD_ACCHI) in h.port.prepared)
    }

    @Test
    fun gesturesGoForwardWithDoubleTapAndBackWithHold() = runTest {
        val h = Harness(this, 0) { front() }
        h.engine.onConnected()
        assertEquals(GameEngine.TITLE_SCREEN, h.port.last())
        h.engine.onGesture(TouchGesture.SINGLE_TAP)
        assertEquals(Phase.TITLE, h.engine.state.phase)
        h.engine.onGesture(TouchGesture.DOUBLE_TAP)
        runCurrent()
        assertEquals(Phase.COUNTDOWN, h.engine.state.phase)
        // 遊んでいる間の 2 回タップは何もしない
        h.engine.onGesture(TouchGesture.DOUBLE_TAP)
        runCurrent()
        assertEquals(Phase.COUNTDOWN, h.engine.state.phase)
        h.engine.onGesture(TouchGesture.HOLD)
        assertEquals(Phase.TITLE, h.engine.state.phase)
        assertEquals(GameEngine.TITLE_SCREEN, h.port.last())
        // 止めたゲームの拍は、もう送られない
        val count = h.port.events.size
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(count, h.port.events.size)
        h.engine.onGesture(TouchGesture.HOLD)
        assertEquals(Phase.STANDBY, h.engine.state.phase)
        assertEquals(FakePort.CLOSE, h.port.last())
        h.engine.onGesture(TouchGesture.HOLD)
        assertEquals(Phase.STANDBY, h.engine.state.phase)
        h.engine.onGesture(TouchGesture.DOUBLE_TAP)
        assertEquals(Phase.TITLE, h.engine.state.phase)
    }

    @Test
    fun gameOverDoubleTapRestartsAndHoldReturnsToTitle() = runTest {
        // 矢印は右、顔も右でアウト
        val h = Harness(this, 3) { t -> if (t in hoi1..hoi1 + 450) right() else front() }
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(result1 + 650 + 1)
        runCurrent()
        assertEquals(Phase.GAME_OVER, h.engine.state.phase)
        h.engine.onGesture(TouchGesture.DOUBLE_TAP)
        runCurrent()
        assertEquals(Phase.COUNTDOWN, h.engine.state.phase)
        assertEquals(text("3"), (h.port.last() as GlassScreen).main)
        h.engine.onGesture(TouchGesture.HOLD)
        assertEquals(Phase.TITLE, h.engine.state.phase)
    }

    @Test
    fun idleTitleReturnsToHome() = runTest {
        val h = Harness(this, 0) { front() }
        h.engine.onConnected()
        advanceTimeBy(GameEngine.IDLE_MS - 1)
        runCurrent()
        assertEquals(Phase.TITLE, h.engine.state.phase)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(Phase.STANDBY, h.engine.state.phase)
        assertEquals(FakePort.CLOSE, h.port.last())
    }

    @Test
    fun disconnectStopsSendingAndReleaseClosesOnlyWhenShowing() = runTest {
        val h = Harness(this, 0) { front() }
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(1_000)
        runCurrent()
        h.engine.onDisconnected()
        assertEquals(1, h.port.forgets)
        val count = h.port.events.size
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(count, h.port.events.size)
        // 切れている間は、始めても何も送らない
        h.engine.start()
        h.engine.onGesture(TouchGesture.DOUBLE_TAP)
        runCurrent()
        assertEquals(count, h.port.events.size)
        // 繋ぎ直すとタイトルから
        h.engine.onConnected()
        assertEquals(GameEngine.TITLE_SCREEN, h.port.last())
        h.engine.release()
        assertEquals(FakePort.CLOSE, h.port.last())
        assertTrue(!h.engine.state.connected)
    }

    @Test
    fun tempoDoesNotGoBelowTheFastest() = runTest {
        // はやい: 540ms から 15ms ずつ、400ms で止まる。毎回、矢印（上）と違う右を向く
        var beat = Tempo.FAST.startMs
        val hois = mutableListOf<Long>()
        var t = 3 * beat
        repeat(14) {
            hois += t + 2 * beat
            t += 4 * beat
            beat = maxOf(Tempo.FAST.fastestMs, beat - GameEngine.STEP_MS)
        }
        val h = Harness(this, 0) { now -> if (hois.any { now in it..it + 300 }) right() else front() }
        h.settings = AppSettings(tempo = Tempo.FAST)
        h.feed()
        h.engine.onConnected()
        h.engine.start()
        advanceTimeBy(t)
        runCurrent()
        assertEquals(Tempo.FAST.fastestMs, h.engine.state.beatMs)
        assertTrue(h.engine.state.streak >= 13)
    }
}
