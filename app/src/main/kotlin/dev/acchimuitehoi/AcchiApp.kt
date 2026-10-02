package dev.acchimuitehoi

import android.content.Context
import android.os.SystemClock
import app.jigglass.glass.CommandManager
import app.jigglass.glass.GestureType
import app.jigglass.glass.GlassClient
import app.jigglass.glass.GlassManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** グラスとの接続の様子（スマホの画面用） */
data class LinkState(
    val deviceName: String? = null,
    val connected: Boolean = false,
    val imuStarted: Boolean = false,
)

/**
 * グラスとの接続を見張り、繋がっている間は SDK の IMU とタッチを [GameEngine] に渡す。
 * Activity ではなく Application のスコープで動かすので、画面を閉じてもプロセスが生きている間は続く。
 * 初回の同意を済ませるまでは、グラスが繋がってもゲームを始めない。
 */
class AcchiApp(context: Context, private val manager: GlassManager) {
    private val store = SettingsStore(context)

    // 想定外の例外でアプリごと落とさない
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> })

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _game = MutableStateFlow(GameState())
    val game: StateFlow<GameState> = _game.asStateFlow()

    private val _link = MutableStateFlow(LinkState())
    val link: StateFlow<LinkState> = _link.asStateFlow()

    /** 「向きの確認」に出す今の向き。正面は [setCheckFront] で決める（最初のサンプルを仮の正面にする） */
    private val _heading = MutableStateFlow<Heading?>(null)
    val heading: StateFlow<Heading?> = _heading.asStateFlow()

    private var engine: GameEngine? = null
    private var commandManager: CommandManager? = null
    private var checkFront: Front? = null
    private var lastSample: PoseSample? = null
    private var lastHeadingUpdate = 0L

    init {
        scope.launch {
            combine(manager.connectedDevice, _settings.map { it.needsConsent() }.distinctUntilChanged()) { client, needsConsent ->
                if (needsConsent) null else client
            }.collectLatest { client ->
                if (client == null) {
                    _link.value = LinkState()
                    _game.value = GameState()
                    return@collectLatest
                }
                // 1 回の接続の中で例外が出ても、次の接続の監視は続ける
                try {
                    runConnection(client)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _link.value = LinkState()
                    _game.value = GameState()
                }
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val new = transform(_settings.value)
        if (new == _settings.value) return
        _settings.value = new
        store.save(new)
    }

    fun start() {
        engine?.start()
    }

    /** グラスの表示を本体のホーム画面に戻す */
    fun stop() {
        engine?.standby()
    }

    /** 「向きの確認」の正面を、今の向きにする */
    fun setCheckFront() {
        val s = lastSample ?: return
        checkFront = Front(s.yaw, s.pitch)
        _heading.value = headingOf(s, checkFront!!, _settings.value.axes)
    }

    /** グラスとの接続を切る。切った後は送信が捨てられるので、表示を閉じるのは切る前に行う */
    fun disconnect() {
        val client = manager.connectedDevice.value ?: return
        val e = engine
        val cm = commandManager
        e?.release()
        cm?.stopImuData()
        scope.launch {
            try {
                manager.disconnect(client)
            } finally {
                // 切れなかったら、ゲームを止めたままにしない
                if (manager.connectedDevice.value != null && engine === e) {
                    cm?.startImuData()
                    e?.onConnected()
                }
            }
        }
    }

    private suspend fun runConnection(client: GlassClient): Unit = coroutineScope {
        val cm = client.createCommandManager()
        val e = GameEngine(
            port = CanvasScreens(SdkCanvasSink(cm), AndroidGlassRaster),
            // ゲームの拍で例外が出ても、接続の見張り（IMU・タッチ）を巻き込んで止めない
            scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]) + CoroutineExceptionHandler { _, _ -> }),
            now = ::monotonicNow,
            settings = { _settings.value },
            onScore = { tempo, streak -> updateSettings { it.withScore(tempo, streak) } },
            onState = { _game.value = it },
        )
        engine = e
        commandManager = cm
        lastHeadingUpdate = 0L
        _link.value = LinkState(deviceName = client.deviceName)

        // 切断するとグラス側で IMU が止まるので、繋がるたびに開始し直す
        launch {
            cm.connected.collect { connected ->
                _link.update { it.copy(connected = connected) }
                if (connected) {
                    cm.startImuData()
                    e.onConnected()
                } else {
                    e.onDisconnected()
                    checkFront = null
                    lastSample = null
                    _heading.value = null
                }
            }
        }
        launch {
            cm.imuDataStarted.collect { started -> _link.update { it.copy(imuStarted = started) } }
        }
        // ツルのタッチ。どのページを開いていても届く
        launch {
            cm.gestureEvents.collect { g ->
                e.onGesture(
                    when (g) {
                        GestureType.SINGLE_TAP -> TouchGesture.SINGLE_TAP
                        GestureType.DOUBLE_TAP -> TouchGesture.DOUBLE_TAP
                        GestureType.HOLD -> TouchGesture.HOLD
                    },
                )
            }
        }
        launch {
            cm.imuData.collect { d ->
                e.onPose(d.yawDegrees, d.pitchDegrees)
                val now = monotonicNow()
                val sample = PoseSample(now, d.yawDegrees, d.pitchDegrees)
                lastSample = sample
                val front = checkFront ?: Front(sample.yaw, sample.pitch).also { checkFront = it }
                // 画面の更新は 1 秒に 10 回まで
                if (now - lastHeadingUpdate >= HEADING_UPDATE_MS) {
                    lastHeadingUpdate = now
                    _heading.value = headingOf(sample, front, _settings.value.axes)
                }
            }
        }

        try {
            awaitCancellation()
        } finally {
            try {
                e.release()
            } finally {
                if (engine === e) {
                    engine = null
                    commandManager = null
                }
                checkFront = null
                lastSample = null
                _heading.value = null
                cm.stopImuData()
            }
        }
    }

    private companion object {
        const val HEADING_UPDATE_MS = 100L

        fun monotonicNow(): Long = SystemClock.elapsedRealtime()
    }
}
