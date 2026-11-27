import java.util.Properties

// AGP 9: Kotlin desteği yerleşik, ayrıca org.jetbrains.kotlin.android uygulanmıyor.
plugins {
    id("com.android.application")
}

val imzaAyarlari = Properties().apply {
    rootProject.file("keystore/keystore.properties").inputStream().use { load(it) }
}

// versionCode = GitHub Actions çalıştırma numarası (yerelde 1).
val surumKodu: Int = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1

// Güncelleyicinin kullanacağı OWNER/REPO: CI'da GITHUB_REPOSITORY, yerelde git remote, en son gradle.properties.
fun gitRemoteRepo(): String? = try {
    val cikti = providers.exec {
        commandLine("git", "remote", "get-url", "origin")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    Regex("github\\.com[:/]+([^/\\s]+/[^/\\s]+?)(?:\\.git)?/?$").find(cikti)?.groupValues?.get(1)
} catch (e: Exception) {
    null
}

val guncellemeRepo: String = providers.environmentVariable("GITHUB_REPOSITORY").orNull
    ?: gitRemoteRepo()
    ?: providers.gradleProperty("egea.guncellemeRepo").get()

android {
    namespace = "com.egea.asistan"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.egea.asistan"
        minSdk = 24
        targetSdk = 34
        versionCode = surumKodu
        versionName = "1.0.$surumKodu"
        buildConfigField("String", "GUNCELLEME_REPO", "\"$guncellemeRepo\"")
    }

    signingConfigs {
        create("egea") {
            storeFile = rootProject.file(imzaAyarlari.getProperty("storeFile"))
            storePassword = imzaAyarlari.getProperty("storePassword")
            keyAlias = imzaAyarlari.getProperty("keyAlias")
            keyPassword = imzaAyarlari.getProperty("keyPassword")
            storeType = "PKCS12"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("egea")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("egea")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // Radyo ve TV oynatma (HLS dahil), arka planda çalma için medya oturumu
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
}
