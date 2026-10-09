import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// 签名：密钥不入库。按优先级读取：
//   1) 环境变量 AIDAILY_KEYSTORE / AIDAILY_KEYSTORE_PASSWORD / AIDAILY_KEY_ALIAS / AIDAILY_KEY_PASSWORD
//   2) 环境变量 AIDAILY_SIGNING_PROPERTIES 指向的 properties 文件
//      （storeFile / storePassword / keyAlias / keyPassword）
// 都没有时 release 产物不签名（assembleRelease 仍可成功）。
// ---------------------------------------------------------------------------
data class SigningInfo(val store: File, val storePassword: String, val alias: String, val keyPassword: String)

fun loadSigning(): SigningInfo? {
    val env = System.getenv()
    env["AIDAILY_KEYSTORE"]?.takeIf { it.isNotBlank() }?.let { path ->
        return SigningInfo(
            File(path),
            env["AIDAILY_KEYSTORE_PASSWORD"].orEmpty(),
            env["AIDAILY_KEY_ALIAS"] ?: "aidaily",
            env["AIDAILY_KEY_PASSWORD"] ?: env["AIDAILY_KEYSTORE_PASSWORD"].orEmpty(),
        )
    }
    val propsPath = env["AIDAILY_SIGNING_PROPERTIES"] ?: return null
    val f = File(propsPath)
    if (!f.exists()) return null
    val p = Properties().apply { FileInputStream(f).use { load(it) } }
    return SigningInfo(
        File(p.getProperty("storeFile")),
        p.getProperty("storePassword"),
        p.getProperty("keyAlias"),
        p.getProperty("keyPassword") ?: p.getProperty("storePassword"),
    )
}

val signing = loadSigning()

android {
    namespace = "com.wikg.aidaily"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.wikg.aidaily"
        minSdk = 31
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.0"
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = signing.store
                storePassword = signing.storePassword
                keyAlias = signing.alias
                keyPassword = signing.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // 字体以 mmap 方式加载，不压缩
        noCompress += listOf("otf", "ttf")
        localeFilters += listOf("zh", "en")
    }
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // ./gradlew :app:testDebugUnitTest -Pscreenshots  → 重新生成 android/screenshots 下的截图
            it.systemProperty("aidaily.screenshots", if (project.hasProperty("screenshots")) "true" else "false")
            it.systemProperty("aidaily.screenshotDir", rootProject.file("screenshots").absolutePath)
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            if (project.hasProperty("screenshots")) {
                it.systemProperty("roborazzi.test.record", "true")
                it.outputs.upToDateWhen { false }
            }
            it.maxHeapSize = "3g"
        }
    }
}

// ---------------------------------------------------------------------------
// MiSans：小米许可协议不允许单独再分发字体文件，所以仓库里不放字体。
// 构建时从小米官网下载（缓存在 Gradle 用户目录），解出所需字重打进 APK 的 assets/fonts/。
// 下载失败时 app 自动退回系统字体。-Pmisans.skip 跳过；-Pmisans.required 下载失败则构建失败。
// ---------------------------------------------------------------------------
abstract class FetchMiSansTask : DefaultTask() {
    @get:Input abstract val zipUrl: Property<String>
    @get:Input abstract val licenseUrl: Property<String>
    @get:Input abstract val weights: ListProperty<String>
    @get:Input abstract val skip: Property<Boolean>
    @get:Input abstract val required: Property<Boolean>
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    private fun download(url: String, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (AI-Daily Android build)")
        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(dest)) error("rename failed: $tmp")
    }

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val fontsDir = File(out, "fonts").apply { mkdirs() }
        if (skip.get()) {
            logger.lifecycle("MiSans: skipped (-Pmisans.skip), using system font")
            return
        }
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        val zip = File(cache, "MiSans.zip")
        try {
            if (!zip.exists() || zip.length() < 1_000_000) {
                logger.lifecycle("MiSans: downloading ${zipUrl.get()} …")
                download(zipUrl.get(), zip)
            }
            ZipFile(zip).use { zf ->
                for (w in weights.get()) {
                    val entry = zf.getEntry("MiSans/otf/MiSans-$w.otf") ?: error("MiSans-$w.otf not found in zip")
                    zf.getInputStream(entry).use { input ->
                        File(fontsDir, "MiSans-$w.otf").outputStream().use { input.copyTo(it) }
                    }
                }
            }
            val license = File(cache, "MiSans-License.pdf")
            runCatching { if (!license.exists()) download(licenseUrl.get(), license) }
            if (license.exists()) license.copyTo(File(fontsDir, "MiSans-License.pdf"), overwrite = true)
            logger.lifecycle("MiSans: bundled ${weights.get()}")
        } catch (e: Exception) {
            zip.delete()
            fontsDir.listFiles()?.forEach { it.delete() }
            if (required.get()) throw GradleException("MiSans download failed", e)
            logger.warn("MiSans: download failed (${e.message}); app will use system font")
        }
    }
}

val fetchMiSans = tasks.register<FetchMiSansTask>("fetchMiSans") {
    zipUrl.set("https://hyperos.mi.com/font-download/MiSans.zip")
    licenseUrl.set("https://hyperos.mi.com/font-download/MiSans%E5%AD%97%E4%BD%93%E7%9F%A5%E8%AF%86%E4%BA%A7%E6%9D%83%E8%AE%B8%E5%8F%AF%E5%8D%8F%E8%AE%AE.pdf")
    weights.set(listOf("Regular", "Medium", "Semibold"))
    skip.set(providers.gradleProperty("misans.skip").isPresent)
    required.set(providers.gradleProperty("misans.required").isPresent)
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/ai-daily/misans"))
    outputDir.set(layout.buildDirectory.dir("generated/misans/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchMiSans, FetchMiSansTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
