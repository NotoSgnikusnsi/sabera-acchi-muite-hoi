package dev.acchimuitehoi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import app.jigglass.glass.CommandManager

/** キャンバスの上の 1 枚の画像の場所と大きさ */
data class Slot(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * グラスのキャンバス（576x360）に置く 2 枚の画像の場所。1 つの id は、置いている間ずっと同じ場所・同じ大きさにする
 * （同じ id を別の場所や大きさで差し替えると、前の画像の一部が画面に残ることがある）。
 * グラスの画像バッファは 380,000 byte で、置いた画像の幅 × 高さ × 2 byte の合計と受信中のデータが入る。
 * 同じ id を差し替える間は古い画像と新しい画像の両方が数えられても収まるよう、真ん中の画像 2 枚分と下の行 1 枚分の合計を
 * [BUFFER_BUDGET] 以内にする。
 */
object CanvasLayout {
    const val WIDTH = 576
    const val HEIGHT = 360

    const val MAIN_ID = 0
    const val STATUS_ID = 1

    /** 画像バッファ 380,000 byte から、受信中の圧縮データの分を残した量 */
    const val BUFFER_BUDGET = 350_000

    /** 真ん中の大きな文字・矢印 */
    val MAIN = Slot(88, 40, 400, 184)

    /** 下の小さな 1 行 */
    val STATUS = Slot(64, 280, 448, 56)
}

/** 画面の中身を 2 値の画像にする（Android の Canvas で描く実物と、テストの偽物）。1 画素 1 byte（0 = 黒、0xFF = 白） */
interface GlassRaster {
    fun main(content: GlassMain, slot: Slot): ByteArray

    fun status(text: String, slot: Slot): ByteArray
}

/** グラスのキャンバスへの送り口（SDK の CommandManager か、テストの記録係） */
interface CanvasSink {
    /** キャンバスを開く（空のキャンバスを送る） */
    fun open()

    fun sendImage(id: Int, slot: Slot, pixels: ByteArray)

    fun removeImage(id: Int)

    /** キャンバスを閉じて、本体のホーム画面に戻す */
    fun close()
}

/**
 * [GlassScreen] を、前に送った画面と比べて変わった画像だけ送る。拍ごとに送る量を減らし、拍の遅れを小さくするため。
 * 真ん中を先に送る（拍の言葉を早く出す）。
 */
class CanvasScreens(private val sink: CanvasSink, private val raster: GlassRaster) : GlassPort {
    private var open = false
    private var main: GlassMain? = null
    private var status: String? = null

    /** 描いた画像を覚えておく。拍の言葉は毎回同じなので、描き直さずに送る */
    private val cache = object : LinkedHashMap<Any, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, ByteArray>?) = size > CACHE_SIZE
    }

    override fun prepare(contents: List<GlassMain>) {
        contents.forEach { c -> cache.getOrPut(c) { raster.main(c, CanvasLayout.MAIN) } }
    }

    override fun show(screen: GlassScreen) {
        if (!open) {
            sink.open()
            open = true
            main = null
            status = null
        }
        if (screen.main != main) {
            val pixels = cache.getOrPut(screen.main) { raster.main(screen.main, CanvasLayout.MAIN) }
            sink.sendImage(CanvasLayout.MAIN_ID, CanvasLayout.MAIN, pixels)
            main = screen.main
        }
        val text = screen.status
        if (text != status) {
            if (text == null) {
                sink.removeImage(CanvasLayout.STATUS_ID)
            } else {
                val pixels = cache.getOrPut(StatusKey(text)) { raster.status(text, CanvasLayout.STATUS) }
                sink.sendImage(CanvasLayout.STATUS_ID, CanvasLayout.STATUS, pixels)
            }
            status = text
        }
    }

    override fun close() {
        sink.close()
        forget()
    }

    override fun forget() {
        open = false
        main = null
        status = null
    }

    private data class StatusKey(val text: String)

    private companion object {
        const val CACHE_SIZE = 24
    }
}

class SdkCanvasSink(private val cm: CommandManager) : CanvasSink {
    override fun open() = cm.sendCanvas(emptyList())

    override fun sendImage(id: Int, slot: Slot, pixels: ByteArray) =
        cm.sendCanvasImage(id = id, x = slot.x, y = slot.y, width = slot.width, height = slot.height, grayscale = pixels)

    override fun removeImage(id: Int) = cm.removeCanvasImage(id)

    override fun close() = cm.closeCanvas()
}

/**
 * 黒地に白で描く。グラスでは黒が透けて見えるので、明るいのは文字と矢印だけになる。
 * グラスは 3bit に量子化するので、アンチエイリアスを切って 2 値にする（RLE が効いて転送も軽くなる）。
 */
object AndroidGlassRaster : GlassRaster {
    override fun main(content: GlassMain, slot: Slot): ByteArray = draw(slot) { canvas ->
        when (content) {
            is GlassMain.Text -> drawLines(canvas, content.lines, slot, maxTextSize = if (content.lines.size <= 1) 170f else 100f)
            is GlassMain.Arrow -> drawArrow(canvas, content.direction, slot)
        }
    }

    override fun status(text: String, slot: Slot): ByteArray = draw(slot) { canvas ->
        drawLines(canvas, listOf(text), slot, maxTextSize = 44f)
    }

    private fun paint(textSize: Float) = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = false
        this.textSize = textSize
    }

    /** 行を縦に等分した帯の中央に 1 行ずつ描く。幅か高さに収まらなければ、その行の文字を小さくする */
    private fun drawLines(canvas: Canvas, lines: List<String>, slot: Slot, maxTextSize: Float) {
        if (lines.isEmpty()) return
        val band = slot.height.toFloat() / lines.size
        lines.forEachIndexed { i, text ->
            val p = paint(maxTextSize)
            val fm = p.fontMetrics
            val heightFit = band * 0.92f / (fm.descent - fm.ascent)
            val widthFit = (slot.width * 0.94f) / p.measureText(text).coerceAtLeast(1f)
            p.textSize = maxTextSize * minOf(1f, heightFit, widthFit)
            val m = p.fontMetrics
            val x = (slot.width - p.measureText(text)) / 2
            val baseline = band * i + (band - (m.descent - m.ascent)) / 2 - m.ascent
            canvas.drawText(text, x, baseline, p)
        }
    }

    /** 太い軸と三角の頭の矢印を、画像の中央に塗りつぶしで描く */
    private fun drawArrow(canvas: Canvas, direction: Direction, slot: Slot) {
        val cx = slot.width / 2f
        val cy = slot.height / 2f
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        // 矢印の長さは、向きの軸に沿った画像の長さの 8〜9 割。頭の幅は画像の短い辺に収める
        val length = if (horizontal) slot.width * 0.8f else slot.height * 0.9f
        val half = length / 2
        val shaft = 24f
        val headHalf = minOf(76f, minOf(slot.width, slot.height) * 0.45f)
        val headLength = minOf(100f, length * 0.45f)
        // 右向きの矢印を作り、向きに合わせて回す
        val path = Path().apply {
            moveTo(-half, -shaft)
            lineTo(half - headLength, -shaft)
            lineTo(half - headLength, -headHalf)
            lineTo(half, 0f)
            lineTo(half - headLength, headHalf)
            lineTo(half - headLength, shaft)
            lineTo(-half, shaft)
            close()
        }
        val degrees = when (direction) {
            Direction.RIGHT -> 0f
            Direction.DOWN -> 90f
            Direction.LEFT -> 180f
            Direction.UP -> 270f
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(degrees)
        canvas.drawPath(path, Paint().apply { color = Color.WHITE; isAntiAlias = false; style = Paint.Style.FILL })
        canvas.restore()
    }

    private inline fun draw(slot: Slot, block: (Canvas) -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(slot.width, slot.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            block(canvas)
            val colors = IntArray(slot.width * slot.height)
            bitmap.getPixels(colors, 0, slot.width, 0, 0, slot.width, slot.height)
            return ByteArray(colors.size) { i -> if (Color.red(colors[i]) >= 128) 0xFF.toByte() else 0 }
        } finally {
            bitmap.recycle()
        }
    }
}
