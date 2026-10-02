package dev.acchimuitehoi

import android.app.Application
import android.content.Context
import app.jigglass.glass.GlassesSDK
import app.jigglass.glass.SdkActivityHost
import app.jigglass.glass.SdkDevicePersistence
import app.jigglass.glass.getGlassManager

class AcchiApplication : Application() {
    lateinit var app: AcchiApp
        private set

    override fun onCreate() {
        super.onCreate()
        GlassesSDK.setProd(true)
        GlassesSDK.setDevicePersistence(SharedPrefsDevicePersistence(this))
        SdkActivityHost.showBleDeviceSelectionDialog = null
        app = AcchiApp(this, getGlassManager(this))
    }
}

/**
 * 前回繋いだグラスを覚えておき、次回の自動接続に使う。Sabera App SDK Samples（Copyright 2026 株式会社jig.jp、
 * Apache License 2.0）の KMP サンプルのものを写して変更したもの。表示は repo の NOTICE と LICENSES/Apache-2.0.txt
 */
private class SharedPrefsDevicePersistence(context: Context) : SdkDevicePersistence {
    private val prefs = context.getSharedPreferences("glasses_sdk", Context.MODE_PRIVATE)

    override var lastDeviceId: String?
        get() = prefs.getString("last_device_id", null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("last_device_id") else putString("last_device_id", value)
            }.apply()
        }
}
