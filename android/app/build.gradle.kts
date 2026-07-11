plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.cmpas.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.cmpas.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Стабильный ключ подписи, закоммичен в репозиторий, чтобы КАЖДЫЙ APK
        // (debug и release, из любого CI-прогона) был подписан одинаково. Это:
        //  1) позволяет установить APK в обход стора и обновлять его «на месте»
        //     (без удаления), т.к. подпись не меняется между сборками;
        //  2) снимает часть предупреждений Play Protect при sideload-установке.
        // Это НЕ ключ для публикации в Google Play — для стора нужен отдельный
        // upload key + Play App Signing (через секреты CI, не в git). См. PRIVACY-DPO.
        create("kompas") {
            storeFile = rootProject.file("keystore/kompas-voice.jks")
            storePassword = "kompasvoice2026"
            keyAlias = "kompas"
            keyPassword = "kompasvoice2026"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("kompas")
        }
        release {
            signingConfig = signingConfigs.getByName("kompas")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.core.splashscreen)

    // Media3 — фоновый аудио-слой (петли «Мягкого фона» + бинауралка за флагом).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)

    implementation(libs.datastore)
    implementation(libs.serialization.json)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
