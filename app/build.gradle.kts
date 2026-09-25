plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasReleaseKeystore: Boolean = !keystorePath.isNullOrBlank() && File(keystorePath).exists()

// Simple version handling - no complex Pair/when that triggers Kotlin DSL bug
val versionNameFinal = System.getenv("GITHUB_REF")?.let { ref ->
    if (ref.startsWith("refs/tags/")) {
        ref.removePrefix("refs/tags/").removePrefix("v").replace("-stable", "")
    } else null
} ?: "1.7.3"

val versionChannel = when {
    System.getenv("GITHUB_REF")?.contains("-beta") == true -> "beta"
    System.getenv("VERSION_CHANNEL") == "beta" -> "beta"
    else -> "stable"
}

val versionCodeFinal = 10703

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
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            buildConfigField("String", "VERSION_CHANNEL", "\"debug\"")
        }
    }

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
}

android.applicationVariants.all {
    val variant = this
    variant.outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val abi = output.filters.find { it.filterType == "ABI" }?.identifier ?: "universal"
        val newName = "AppsPerms-${versionNameFinal}-${versionChannel}-${abi}-${variant.buildType.name}.apk"
        output.outputFileName = newName
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
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    testImplementation("junit:junit:4.13.2")
}
