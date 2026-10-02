package dev.acchimuitehoi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jigglass.glass.GlassManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** スマホの画面。上に接続・スタートとやめる・テンポ・遊び方を出し、下に向きの調整（たたんである）・安全の注意・ライセンスを置く */
@Composable
fun MainScreen(app: AcchiApp, manager: GlassManager) {
    val client by manager.connectedDevice.collectAsState()
    val link by app.link.collectAsState()
    val game by app.game.collectAsState()
    val settings by app.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("あっち向いてホイ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (client == null) {
            ConnectSection(manager)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${link.deviceName ?: "SABERA"}: ${if (link.connected) "接続中" else "接続待ち"}",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = app::disconnect) { Text("切断") }
            }
        }

        GameCard(game, settings)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = app::start,
                enabled = game.connected,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(if (game.phase == Phase.GAME_OVER) "もう一度" else "スタート", fontSize = 18.sp) }
            OutlinedButton(
                onClick = app::stop,
                enabled = game.connected && game.phase != Phase.STANDBY,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("やめる", fontSize = 18.sp) }
        }

        TempoRow(settings.tempo) { t -> app.updateSettings { it.copy(tempo = t) } }

        HowToPlay()

        HorizontalDivider()
        HeadingSection(app, settings)
        HorizontalDivider()
        SafetySection()
        LegalSection()
    }
}

@Composable
private fun ConnectSection(manager: GlassManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("グラスが未接続。公式アプリを使っていたら、先に止めておく。前回のグラスがあれば自動でつながる。")
    Button(
        onClick = {
            error = null
            scanning = true
            scope.launch {
                try {
                    manager.showAutomaticSelectionDialog(context)
                } catch (e: Throwable) {
                    error = "つなげなかった。グラスの電源と Bluetooth を確かめて、もう一度押す"
                } finally {
                    scanning = false
                }
            }
        },
        enabled = !scanning,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (scanning) "さがしています..." else "グラスを選んでつなぐ") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/** グラスにいま出ているものと、記録 */
@Composable
private fun GameCard(game: GameState, settings: AppSettings) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                glassText(game),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "れんぞく ${game.streak}　ベスト ${settings.bestFor(settings.tempo)}（${settings.tempo.label}）",
                style = MaterialTheme.typography.bodyLarge,
            )
            phaseHint(game)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/** グラスの真ん中に出ているものを、スマホの文字で表す */
private fun glassText(game: GameState): String {
    if (!game.connected) return "—"
    val main = game.screen?.main ?: return "（ホーム画面）"
    return when (main) {
        is GlassMain.Text -> main.lines.joinToString("\n")
        is GlassMain.Arrow -> arrowOf(main.direction)
    }
}

private fun arrowOf(d: Direction) = when (d) {
    Direction.UP -> "↑"
    Direction.DOWN -> "↓"
    Direction.LEFT -> "←"
    Direction.RIGHT -> "→"
}

private fun phaseHint(game: GameState): String? = when {
    !game.connected -> "グラスにつなぐと始められる"
    game.phase == Phase.STANDBY -> "グラスのツルを 2 回タップすると、タイトルが出る"
    game.phase == Phase.GAME_OVER && game.endReason == EndReason.FOULS -> "「はやい」か「おそい」が ${GameEngine.FOUL_LIMIT} 回つづいたので終わった"
    game.phase == Phase.GAME_OVER && game.endReason == EndReason.NO_SENSOR -> "グラスから頭の向きが届かなかった。「切断」してから、つなぎ直して試す"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TempoRow(selected: Tempo, onSelect: (Tempo) -> Unit) {
    Column {
        Text("テンポ", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tempo.entries.forEach { t ->
                FilterChip(selected = t == selected, onClick = { onSelect(t) }, label = { Text(t.label) })
            }
        }
        Text("セーフが続くと、だんだん速くなる。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HowToPlay() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("遊び方", style = MaterialTheme.typography.titleMedium)
        Text(
            "グラスに「あっち」「むいて」と 1 拍ずつ出たあと、次の拍で矢印が出る。" +
                "矢印が出る拍（ホイ）に合わせて、上・下・左・右のどれかへ顔を向ける。" +
                "矢印と同じ向きを向いたらアウト、違う向きならセーフで、次の回へ進む。",
        )
        Text(
            "矢印が出る 0.25 秒より前に向くと「はやい」、次の拍までに向かないと「おそい」になる。" +
                "その回はセーフにも数えずに次の回へ進み、${GameEngine.FOUL_LIMIT} 回つづくと終わる。",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("グラスのツルの操作", style = MaterialTheme.typography.titleSmall)
        Text("・2 回タップ: タイトルを出す・スタート・もう一度\n・長押し: やめてタイトルに戻る（タイトルで長押しするとホーム画面に戻る）")
    }
}

/** 向きの確認と左右・上下の入れ替え。実機で矢印と顔の向きが逆に判定されたときに使う */
@Composable
private fun HeadingSection(app: AcchiApp, settings: AppSettings) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "向きの確認と調整 ▲" else "向きの確認と調整 ▼") }
    if (!open) return
    val heading by app.heading.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val h = heading
        Text(
            if (h == null) {
                "グラスにつなぐと、今の向きが出る"
            } else {
                "今の向き: ${h.direction?.label ?: "正面"}（右 ${h.right.roundToInt()}°・上 ${h.up.roundToInt()}°）"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = app::setCheckFront, enabled = h != null) { Text("今の向きを正面にする") }
        Text(
            "正面を決めてから、右・上を向いて、同じ向きが出るかを確かめる。逆に出たら下のスイッチで入れ替える。" +
                "ゲームでは、毎回「あっち」の拍の間の向きを正面にする。",
            style = MaterialTheme.typography.bodySmall,
        )
        SwitchRow("左右を入れ替える", settings.axes.flipLeftRight) { v ->
            app.updateSettings { it.copy(axes = it.axes.copy(flipLeftRight = v)) }
        }
        SwitchRow("上下を入れ替える", settings.axes.flipUpDown) { v ->
            app.updateSettings { it.copy(axes = it.axes.copy(flipUpDown = v)) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SafetySection() {
    Column {
        Text("安全のために", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(SAFETY_POINTS.joinToString("\n") { "・$it" })
    }
}
