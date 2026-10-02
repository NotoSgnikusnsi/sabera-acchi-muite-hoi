plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.mikepenz.aboutlibraries.plugin.android")
}

android {
    namespace = "dev.acchimuitehoi"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.acchimuitehoi"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// repo の直下の PRIVACY.md・TERMS.md・NOTICE・LICENSE・LICENSES/ を assets/legal/ に写して、アプリの画面で読む。
// 文面を repo とアプリで二重に持たないため
abstract class CopyLegalAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val files: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenses: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.resolve("legal")
        out.deleteRecursively()
        out.mkdirs()
        files.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
        licenses.get().asFile.copyRecursively(out.resolve("LICENSES"), overwrite = true)
    }
}

val copyLegalAssets = tasks.register<CopyLegalAssets>("copyLegalAssets") {
    files.from(rootProject.file("PRIVACY.md"), rootProject.file("TERMS.md"), rootProject.file("NOTICE"), rootProject.file("LICENSE"))
    licenses.set(rootProject.layout.projectDirectory.dir("LICENSES"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLegalAssets, CopyLegalAssets::outputDir)
    }
}

// APK に入る依存ライブラリとライセンスの一覧を、ビルドのときに res/raw/aboutlibraries.json に書き出し、「ライセンス」画面で読む。
// 許可していないライセンスの依存が入ると、ビルドが失敗する。失敗したら、そのライセンスの全文と著作権表示を LICENSES/ に置き、
// Legal.kt の LegalDoc と LegalScreen.kt に足してから、allowedLicenses か allowedLicensesMap に足す（docs/development.md の「依存ライブラリのライセンス」）
aboutLibraries {
    library {
        // Sabera App SDK の表示は NOTICE に書く（SDK は Apache License 2.0 ではなく SDK 利用規約で提供される）
        exclusionPatterns.add(Regex("jp\\.jig\\.sabera\\..*").toPattern())
        requireLicense = true
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.add("Apache-2.0")
        allowedLicensesMap = mapOf("MIT" to listOf("org.slf4j:slf4j-api"))
    }
}

dependencies {
    implementation("jp.jig.sabera.app.sdk:sabera-app-core:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(compose.material3)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.mikepenz:aboutlibraries-core:15.2.0")

    testImplementation(kotlin("test"))
    // GameEngine の拍を仮想時間で確かめる
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
