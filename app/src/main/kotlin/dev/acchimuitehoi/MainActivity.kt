package dev.acchimuitehoi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import app.jigglass.ble.BleCompanionDeviceService
import app.jigglass.ble.BleDeviceSelector
import app.jigglass.glass.SdkActivityHost
import app.jigglass.glass.getGlassManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * デバイス選択（BleDeviceSelector を Activity に結び付ける部分）は、Sabera App SDK Samples（Copyright 2026 株式会社jig.jp、
 * Apache License 2.0）の KMP サンプルの Activity を写して縮め、変更したもの。表示は repo の NOTICE と LICENSES/Apache-2.0.txt
 */
class MainActivity : ComponentActivity() {

    private lateinit var deviceSelector: BleDeviceSelector
    private lateinit var showSelectionDialog: (Context, (String?) -> Unit) -> Unit
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // デバイス選択ダイアログは SDK 側にあり、表示に Activity が要る
        deviceSelector = BleDeviceSelector(this)
        showSelectionDialog = { _: Context, callback: (String?) -> Unit ->
            deviceSelector.showDialog(scope = scope, singleTarget = false, callback = callback)
        }
        SdkActivityHost.showBleDeviceSelectionDialog = showSelectionDialog

        val app = (application as AcchiApplication).app
        // 初回の同意（安全の注意・利用条件・プライバシー・ライセンス）を済ませるまでは、グラスへの接続も権限の要求も始めない
        if (!app.settings.value.needsConsent()) startUsing()
        val manager = getGlassManager(applicationContext)

        setContent {
            MaterialTheme {
                val settings by app.settings.collectAsState()
                if (settings.needsConsent()) {
                    ConsentScreen(
                        onAccept = {
                            app.updateSettings { it.acceptConsent() }
                            startUsing()
                        },
                        onDecline = { finish() },
                    )
                } else {
                    MainScreen(app = app, manager = manager)
                }
            }
        }
    }

    private fun startUsing() {
        BleCompanionDeviceService.connectToLastDevice(this)
        requestBlePermissionsIfNeeded()
    }

    override fun onDestroy() {
        // 破棄した Activity を SDK が掴んだままにしない。再生成で次の Activity が入れた分は消さない
        if (SdkActivityHost.showBleDeviceSelectionDialog === showSelectionDialog) {
            SdkActivityHost.showBleDeviceSelectionDialog = null
        }
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use Activity Result API", ReplaceWith("registerForActivityResult(...)"))
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (deviceSelector.onActivityResult(requestCode, resultCode, data, scope)) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun requestBlePermissionsIfNeeded() {
        // minSdk 31 なので BLUETOOTH_SCAN / CONNECT だけでよい
        val perms = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            @Suppress("DEPRECATION")
            requestPermissions(perms, REQUEST_BLE_PERMISSIONS)
        }
    }

    private companion object {
        const val REQUEST_BLE_PERMISSIONS = 1001
    }
}
