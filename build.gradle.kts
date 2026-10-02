// 公式 KMP サンプルと同じ版に揃える
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
    // 依存ライブラリのライセンスの一覧を作る。AboutLibraries の README は、15.2.0 を Compose 1.12・AGP 9・Kotlin 2.4 向けの版としている
    id("com.mikepenz.aboutlibraries.plugin.android") version "15.2.0" apply false
}
