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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 初回の同意。安全の注意・利用条件・プライバシーポリシー・ライセンスを見せて、同意したら使い始める */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var openName by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("あっち向いてホイ を遊ぶ前に", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("安全の注意", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(SAFETY_POINTS.joinToString("\n") { "・$it" }, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text("このアプリについて", style = MaterialTheme.typography.titleMedium)
        Text(
            "個人が作った SABERA 対応のアプリで、SABERA の提供元の製品ではない。現状のまま無償で提供し、動作を保証しない。" +
                "グラスとの通信は Sabera App SDK の公開された機能だけで行い、グラス本体とファームウェアは変更しない。" +
                "頭の向きは判定のためにメモリで扱うだけで、保存も送信もしない（インターネットの権限が無い）。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openName = LegalDoc.TERMS.name }) { Text("利用条件") }
            OutlinedButton(onClick = { openName = LegalDoc.PRIVACY.name }) { Text("プライバシー") }
            OutlinedButton(onClick = { openName = LegalDoc.NOTICE.name }) { Text("ライセンス") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("安全の注意・利用条件・プライバシーポリシーを読み、同意する", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth()) { Text("同意して始める") }
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("同意しない（閉じる）") }
    }
    LegalDoc.entries.firstOrNull { it.name == openName }?.let { doc -> LegalDocDialog(doc) { openName = null } }
}

/** 文書を全画面で読む。ライセンスは NOTICE・Opus・依存ライブラリの一覧・SLF4J・Apache をまとめて出す */
@Composable
fun LegalDocDialog(doc: LegalDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember(doc) {
        if (doc == LegalDoc.NOTICE) {
            listOf(
                readLegal(context, LegalDoc.NOTICE),
                "---- " + LegalDoc.OPUS.title,
                readOpusNotice(context),
                "---- このアプリが使っているライブラリ",
                readLibraries(context),
                "---- " + LegalDoc.SLF4J.title,
                readLegal(context, LegalDoc.SLF4J),
                "---- " + LegalDoc.APACHE.title,
                readLegal(context, LegalDoc.APACHE),
            ).joinToString("\n\n")
        } else {
            readLegal(context, doc)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (doc == LegalDoc.NOTICE) "ライセンス" else doc.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("閉じる") }
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (doc == LegalDoc.NOTICE) FontFamily.Monospace else FontFamily.Default,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** メインの画面の下に置く: ライセンス・プライバシー・利用条件をいつでも読める */
@Composable
fun LegalSection() {
    var open by remember { mutableStateOf<LegalDoc?>(null) }
    Column {
        Text("ライセンス・プライバシー・利用条件", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open = LegalDoc.NOTICE }) { Text("ライセンス") }
            OutlinedButton(onClick = { open = LegalDoc.PRIVACY }) { Text("プライバシー") }
            OutlinedButton(onClick = { open = LegalDoc.TERMS }) { Text("利用条件") }
        }
    }
    open?.let { doc -> LegalDocDialog(doc) { open = null } }
}
