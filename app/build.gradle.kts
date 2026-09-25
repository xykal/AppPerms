plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Keystore release diambil dari environment (dipakai di CI lewat GitHub Secrets)
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasReleaseKeystore: Boolean =
    !keystorePath.isNullOrBlank() && File(keystorePath).exists()

// Version handling: support channel like stable, beta, pre, dev
// Tag format: v1.7.2, v1.7.2-stable, v1.7.2-beta, v1.7.2-pre, v1.7.3-rc1
// Env var VERSION_CHANNEL bisa override, misal: stable, beta, pre, rc, dev
val baseVersionName = "1.7.3" // bump untuk next release
val envChannel = System.getenv("VERSION_CHANNEL")?.trim()?.lowercase()
val gitTag = System.getenv("GITHUB_REF")?.let { ref ->
    if (ref.startsWith("refs/tags/")) ref.removePrefix("refs/tags/").removePrefix("v") else null
} // contoh: 1.7.2, 1.7.2-beta, 1.7.2-stable

// Tentukan channel dan versionName final
val (versionNameFinal, versionChannel) = when {
    // Jika tag ada, pakai tag sebagai versionName, extract channel
    gitTag != null -> {
        val channel = when {
            gitTag.contains("-beta", ignoreCase = true) -> "beta"
            gitTag.contains("-pre", ignoreCase = true) -> "pre"
            gitTag.contains("-rc", ignoreCase = true) -> "rc"
            gitTag.contains("-alpha", ignoreCase = true) -> "alpha"
            gitTag.contains("-stable", ignoreCase = true) -> "stable"
            else -> "stable"
        }
        // Bersihkan -stable suffix untuk versionName yang bersih, tapi simpan channel
        val cleanName = gitTag.replace("-stable", "", ignoreCase = true)
        Pair(cleanName, channel)
    }
    // Jika env channel diset
    !envChannel.isNullOrBlank() -> {
        val ch = envChannel
        val name = if (ch == "stable") baseVersionName else "$baseVersionName-$ch"
        Pair(name, ch)
    }
    // Default: dev build dari main
    else -> {
        val commit = System.getenv("GITHUB_SHA")?.take(7) ?: "local"
        Pair("$baseVersionName-dev+$commit", "dev")
    }
}

// VersionCode: 1.7.3 -> 10703, plus channel offset
// stable = +0, rc = +100, beta = +200, pre/alpha = +300, dev = +500
// Contoh: 1.7.2 stable = 10720, 1.7.2 beta = 10920, 1.7.3 dev = 11203
fun parseVersionCode(name: String): Int {
    // Ambil major.minor.patch
    val clean = name.split("-")[0].split("+")[0] // 1.7.2 dari 1.7.2-beta+abc
    val parts = clean.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}
val baseCode = parseVersionCode(versionNameFinal)
val channelOffset = when (versionChannel) {
    "stable" -> 0
    "rc" -> 100
    "beta" -> 200
    "pre", "alpha" -> 300
    "dev" -> 500
    else -> 0
}
val versionCodeFinal = baseCode + channelOffset

println(">> AppsPerms Build: versionName=$versionNameFinal channel=$versionChannel versionCode=$versionCodeFinal hasKeystore=$hasReleaseKeystore")

android {
    namespace = "app.appsperms"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.appsperms"
        minSdk = 24
        targetSdk = 34
        versionCode = versionCodeFinal
        versionName = versionNameFinal
        vectorDrawables.useSupportLibrary = true

        // BuildConfig fields untuk ditampilkan di About & Settings
        buildConfigField("String", "VERSION_CHANNEL", "\"$versionChannel\"")
        buildConfigField("String", "VERSION_FULL", "\"$versionNameFinal ($versionChannel)\"")
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Tambah channel ke apk name via applicationVariants (di bawah)
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            // Debug pakai debug keystore bawaan
            buildConfigField("String", "VERSION_CHANNEL", "\"debug\"")
        }
    }

    // === ABI Splits: universal + arm64-v8a + armeabi-v7a + x86_64 ===
    // Karena app tidak punya native lib (.so), satu APK universal jalan di semua ABI
    // Tapi kita tetap generate splits untuk masa depan & untuk feed yang menarik (banyak asset)
    // User bisa pilih universal (recommended, ~1.9MB) atau arch-specific (sedikit lebih kecil jika ada .so)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }

    // Custom APK naming: AppsPerms-1.7.3-stable-universal-release.apk, etc.
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = output.getFilter(com.android.build.api.variant.FilterConfiguration.FilterType.ABI) ?: "universal"
            val channel = versionChannel
            val ver = versionNameFinal
            // Format: AppsPerms-1.7.3-stable-universal-release.apk atau AppsPerms-1.7.3-beta-arm64-v8a-release.apk
            val newName = "AppsPerms-${ver}-${channel}-${abi}-${variant.buildType.name}.apk"
            output.outputFileName = newName
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Shizuku — akses AppOps lewat ADB/root privilege tanpa root permanen
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Buka blokir hidden API (IAppOpsService) di Android 9+
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    testImplementation("junit:junit:4.13.2")
}
