package dev.acchimuitehoi

import kotlin.math.abs
import kotlin.math.max

/** 顔を向ける向き、またはグラスの矢印の向き。左右はグラスをかけた人から見た向き */
enum class Direction(val label: String) {
    UP("上"),
    DOWN("下"),
    LEFT("左"),
    RIGHT("右"),
}

/** IMU の 1 サンプル。[timeMs] はスマホが受け取った時刻（単調な時計） */
data class PoseSample(val timeMs: Long, val yaw: Float, val pitch: Float)

/**
 * IMU の角度の差を、右向き・上向きの角度に直す。
 * 公式サンプルは yaw が増える向きを左、pitch は上向きを負として扱っているので、既定ではどちらも符号を反転する。
 * 実機で逆に出たときのために、アプリの設定で左右・上下を入れ替えられる。
 */
data class Axes(val flipLeftRight: Boolean = false, val flipUpDown: Boolean = false) {
    fun right(yawDelta: Float): Float = if (flipLeftRight) yawDelta else -yawDelta

    fun up(pitchDelta: Float): Float = if (flipUpDown) pitchDelta else -pitchDelta
}

/** 角度の差 a - b を -180〜180° に収める（yaw が ±180° をまたいでも差を正しく出す） */
fun angleDiff(a: Float, b: Float): Float {
    var d = (a - b) % 360f
    if (d >= 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/**
 * 正面からの角度で、どの向きを向いたかを決める。左右は [YAW_DEG]、上下は [PITCH_DEG] を超えたら向いたとみなし、
 * 両方超えたら、しきい値に対してより大きく動いた軸を採る。
 */
object DirectionRule {
    /** 左右を向いたとみなす角度 */
    const val YAW_DEG = 20f

    /** 上下を向いたとみなす角度。首を縦に振れる幅は横より狭いので小さくする */
    const val PITCH_DEG = 15f

    fun classify(right: Float, up: Float): Direction? {
        val rx = abs(right) / YAW_DEG
        val ry = abs(up) / PITCH_DEG
        if (max(rx, ry) < 1f) return null
        return if (rx >= ry) {
            if (right > 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (up > 0) Direction.UP else Direction.DOWN
        }
    }
}

/** 正面の向き（yaw と pitch の平均） */
data class Front(val yaw: Float, val pitch: Float)

/**
 * サンプルの平均で正面を決める。yaw は ±180° をまたぐことがあるので、最初のサンプルからの差で平均する。
 * サンプルが無ければ null
 */
fun frontOf(samples: List<PoseSample>): Front? {
    if (samples.isEmpty()) return null
    val first = samples.first()
    var yawSum = 0.0
    var pitchSum = 0.0
    for (s in samples) {
        yawSum += angleDiff(s.yaw, first.yaw)
        pitchSum += s.pitch
    }
    val n = samples.size
    return Front(yaw = first.yaw + (yawSum / n).toFloat(), pitch = (pitchSum / n).toFloat())
}

/** 正面から見た今の向き（右向き・上向きの角度と、向いたとみなす向き） */
data class Heading(val right: Float, val up: Float) {
    val direction: Direction? get() = DirectionRule.classify(right, up)
}

fun headingOf(sample: PoseSample, front: Front, axes: Axes): Heading =
    Heading(right = axes.right(angleDiff(sample.yaw, front.yaw)), up = axes.up(sample.pitch - front.pitch))

/** 1 回の「ホイ」の判定結果 */
sealed interface Judgement {
    /** 受け付ける時間の中で [direction] を向いた */
    data class Turned(val direction: Direction) : Judgement

    /** 「ホイ」より前に向いた（フライング） */
    data object Early : Judgement

    /** 締め切りまでに向かなかった */
    data object Late : Judgement

    /** IMU のサンプルが届いていない（判定できない） */
    data object NoSensor : Judgement
}

/**
 * 1 回の判定に使う時刻（どれもスマホの単調な時計の ms）。
 * - [frontFrom]〜[watchFrom] のサンプルの平均を正面にする
 * - [watchFrom] から向きを見はじめ、最初にしきい値を超えた時刻で決める
 * - その時刻が [hoiMs] - [earlyMs] より前ならフライング、[deadlineMs] より後（または超えない）なら遅い
 */
data class RoundTiming(
    val frontFrom: Long,
    val watchFrom: Long,
    val hoiMs: Long,
    val earlyMs: Long,
    val deadlineMs: Long,
)

/**
 * 判定する。ホイの拍で矢印を出すと同時に顔を向けてもらうので、矢印を見てから向きを変えても間に合わないよう、
 * 最初にしきい値を超えた向きで決める（あとで向き直しても変わらない）。
 */
fun judge(samples: List<PoseSample>, timing: RoundTiming, axes: Axes): Judgement {
    val front = frontOf(samples.filter { it.timeMs >= timing.frontFrom && it.timeMs < timing.watchFrom })
        ?: return Judgement.NoSensor
    val watched = samples.filter { it.timeMs >= timing.watchFrom && it.timeMs <= timing.deadlineMs }
    if (watched.isEmpty()) return Judgement.NoSensor
    for (s in watched) {
        val direction = headingOf(s, front, axes).direction ?: continue
        return if (s.timeMs < timing.hoiMs - timing.earlyMs) Judgement.Early else Judgement.Turned(direction)
    }
    return Judgement.Late
}
