package dev.acchimuitehoi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirectionTest {
    @Test
    fun angleDiffWrapsAround180() {
        assertEquals(20f, angleDiff(-170f, 170f))
        assertEquals(-20f, angleDiff(170f, -170f))
        assertEquals(0f, angleDiff(360f, 0f))
    }

    @Test
    fun classifyNeedsThreshold() {
        assertNull(DirectionRule.classify(19f, 14f))
        assertEquals(Direction.RIGHT, DirectionRule.classify(20f, 0f))
        assertEquals(Direction.LEFT, DirectionRule.classify(-25f, 5f))
        assertEquals(Direction.UP, DirectionRule.classify(0f, 15f))
        assertEquals(Direction.DOWN, DirectionRule.classify(3f, -16f))
    }

    @Test
    fun classifyPicksAxisThatMovedMoreRelativeToItsThreshold() {
        // 右 30°（しきい値の 1.5 倍）と上 18°（1.2 倍）なら右
        assertEquals(Direction.RIGHT, DirectionRule.classify(30f, 18f))
        // 右 22°（1.1 倍）と上 30°（2 倍）なら上
        assertEquals(Direction.UP, DirectionRule.classify(22f, 30f))
    }

    @Test
    fun defaultAxesFollowTheOfficialSample() {
        // 公式サンプル: yaw が増えると左、pitch は上向きが負
        val axes = Axes()
        assertEquals(Direction.LEFT, headingOf(PoseSample(0, 30f, 0f), Front(0f, 0f), axes).direction)
        assertEquals(Direction.UP, headingOf(PoseSample(0, 0f, -20f), Front(0f, 0f), axes).direction)
        val flipped = Axes(flipLeftRight = true, flipUpDown = true)
        assertEquals(Direction.RIGHT, headingOf(PoseSample(0, 30f, 0f), Front(0f, 0f), flipped).direction)
        assertEquals(Direction.DOWN, headingOf(PoseSample(0, 0f, -20f), Front(0f, 0f), flipped).direction)
    }

    @Test
    fun frontAveragesAcrossTheWrap() {
        val front = frontOf(listOf(PoseSample(0, 179f, 2f), PoseSample(1, -179f, 4f)))!!
        assertEquals(0f, angleDiff(front.yaw, 180f), 0.001f)
        assertEquals(3f, front.pitch, 0.001f)
        assertNull(frontOf(emptyList()))
    }

    private val timing = RoundTiming(frontFrom = 300, watchFrom = 600, hoiMs = 1200, earlyMs = 250, deadlineMs = 1720)

    private fun samples(vararg points: Pair<Long, Float>) = points.map { (t, yaw) -> PoseSample(t, yaw, 0f) }

    @Test
    fun judgeUsesTheFirstCrossing() {
        // 1250ms に右（yaw 減）へ、その後に左へ向き直しても右のまま
        val s = samples(400L to 0f, 500L to 0f, 700L to 0f, 1250L to -25f, 1400L to 40f)
        assertEquals(Judgement.Turned(Direction.RIGHT), judge(s, timing, Axes()))
    }

    @Test
    fun judgeEarlyWhenTurnedBeforeHoiMinusTolerance() {
        val s = samples(400L to 0f, 700L to 0f, 900L to -25f)
        assertEquals(Judgement.Early, judge(s, timing, Axes()))
        // 許す幅の中（ホイの 250ms 前まで）なら受け付ける
        val ok = samples(400L to 0f, 700L to 0f, 950L to -25f)
        assertEquals(Judgement.Turned(Direction.RIGHT), judge(ok, timing, Axes()))
    }

    @Test
    fun judgeLateWhenNoCrossingBeforeDeadline() {
        val s = samples(400L to 0f, 700L to 0f, 1300L to -10f, 1800L to -40f)
        assertEquals(Judgement.Late, judge(s, timing, Axes()))
    }

    @Test
    fun judgeNoSensorWithoutSamples() {
        assertEquals(Judgement.NoSensor, judge(emptyList(), timing, Axes()))
        // 正面を決める区間にサンプルが無い
        assertEquals(Judgement.NoSensor, judge(samples(700L to 0f, 1300L to -30f), timing, Axes()))
        // 見はじめてからサンプルが無い
        assertEquals(Judgement.NoSensor, judge(samples(400L to 0f), timing, Axes()))
    }
}
