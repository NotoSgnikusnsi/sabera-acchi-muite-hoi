package dev.acchimuitehoi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasScreensTest {
    private class RecordingSink : CanvasSink {
        val calls = mutableListOf<String>()

        override fun open() {
            calls += "open"
        }

        override fun sendImage(id: Int, slot: Slot, pixels: ByteArray) {
            calls += "image $id ${slot.x},${slot.y},${slot.width}x${slot.height}"
        }

        override fun removeImage(id: Int) {
            calls += "remove $id"
        }

        override fun close() {
            calls += "close"
        }
    }

    private class CountingRaster : GlassRaster {
        var drawn = 0

        override fun main(content: GlassMain, slot: Slot): ByteArray {
            drawn++
            return ByteArray(slot.width * slot.height)
        }

        override fun status(text: String, slot: Slot): ByteArray {
            drawn++
            return ByteArray(slot.width * slot.height)
        }
    }

    private val main = "image 0 88,40,400x184"
    private val status = "image 1 64,280,448x56"

    @Test
    fun sendsOnlyWhatChangedAndKeepsEachIdInOnePlace() {
        val sink = RecordingSink()
        val port = CanvasScreens(sink, CountingRaster())
        port.show(GlassScreen(GlassMain.Text("あっち"), "れんぞく 0"))
        port.show(GlassScreen(GlassMain.Text("むいて"), "れんぞく 0"))
        port.show(GlassScreen(GlassMain.Text("むいて"), "れんぞく 0"))
        port.show(GlassScreen(GlassMain.Arrow(Direction.LEFT), null))
        assertEquals(listOf("open", main, status, main, main, "remove 1"), sink.calls)
    }

    @Test
    fun closeThenShowOpensAgainAndResendsEverything() {
        val sink = RecordingSink()
        val port = CanvasScreens(sink, CountingRaster())
        port.show(GlassScreen(GlassMain.Text("あっち"), "れんぞく 0"))
        port.close()
        port.show(GlassScreen(GlassMain.Text("あっち"), "れんぞく 0"))
        assertEquals(listOf("open", main, status, "close", "open", main, status), sink.calls)
    }

    @Test
    fun afterForgetTheNextShowOpensAndResendsWithoutSendingAnythingOnForget() {
        // 同じ接続のまま切れて繋ぎ直したとき、グラスは何も表示していないので、同じ画面でも送り直す
        val sink = RecordingSink()
        val port = CanvasScreens(sink, CountingRaster())
        port.show(GameEngine.TITLE_SCREEN)
        port.forget()
        assertEquals(listOf("open", main, status), sink.calls)
        port.show(GameEngine.TITLE_SCREEN)
        assertEquals(listOf("open", main, status, "open", main, status), sink.calls)
    }

    @Test
    fun preparedScreensAreNotDrawnAgain() {
        val raster = CountingRaster()
        val port = CanvasScreens(RecordingSink(), raster)
        port.prepare(GameEngine.PREPARED)
        val drawn = raster.drawn
        port.show(GlassScreen(GlassMain.Arrow(Direction.UP), null))
        port.show(GlassScreen(GlassMain.Text(GameEngine.WORD_ACCHI), null))
        assertEquals(drawn, raster.drawn)
    }

    @Test
    fun repeatedWordsAreDrawnOnce() {
        val raster = CountingRaster()
        val port = CanvasScreens(RecordingSink(), raster)
        repeat(3) {
            port.show(GlassScreen(GlassMain.Text("あっち"), "れんぞく 1"))
            port.show(GlassScreen(GlassMain.Text("むいて"), "れんぞく 1"))
        }
        assertEquals(3, raster.drawn)
    }

    @Test
    fun slotsFitTheCanvasAndTheImageBuffer() {
        for (s in listOf(CanvasLayout.MAIN, CanvasLayout.STATUS)) {
            assertTrue(s.x >= 0 && s.y >= 0 && s.x + s.width <= CanvasLayout.WIDTH && s.y + s.height <= CanvasLayout.HEIGHT)
        }
        fun bytes(s: Slot) = s.width * s.height * 2
        // 同じ id を差し替える間に古い画像と新しい画像の両方が数えられても、画像バッファに収まる
        val replacingMain = bytes(CanvasLayout.MAIN) * 2 + bytes(CanvasLayout.STATUS)
        val replacingStatus = bytes(CanvasLayout.MAIN) + bytes(CanvasLayout.STATUS) * 2
        assertTrue(replacingMain <= CanvasLayout.BUFFER_BUDGET, "replacingMain=$replacingMain")
        assertTrue(replacingStatus <= CanvasLayout.BUFFER_BUDGET, "replacingStatus=$replacingStatus")
        // 2 枚は重ならない
        assertTrue(CanvasLayout.MAIN.y + CanvasLayout.MAIN.height <= CanvasLayout.STATUS.y)
    }
}
