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

        // Адрес приёмника аналитики (О-260817-14) — из конфигурации сборки, не
        // из кода: переопределяется свойством Gradle `analyticsIngestUrl`
        // (например, -PanalyticsIngestUrl=... в CI), по умолчанию — прод-адрес
        // существующего приёмника ПРАКТИКИ. Путь сверен с фактическим роутом
        // (`compas-psy/cmpas.ru`, `src/app/api/ingest/route.ts` — Next.js App
        // Router кладёт его на `/api/ingest`, а не на `/ingest`).
        buildConfigField(
            "String",
            "ANALYTICS_INGEST_URL",
            "\"${project.findProperty("analyticsIngestUrl") ?: "https://cmpas.ru/api/ingest"}\"",
        )

        // Секрет POST /ingest ТОЛЬКО для МОМЕНТОВ (О-260817-17,
        // `verifyIngestSecret` на приёмнике) — отдельный, НЕ общий с ПРАКТИКОЙ
        // и ЗАПИСКАМИ. Только из конфигурации сборки, никогда не строкой в
        // коде: без значения по умолчанию нарочно. Передаётся как
        // -PanalyticsIngestSecretMoments=... в CI/release-сборке из секрета
        // репозитория ANALYTICS_INGEST_SECRET_MOMENTS; в локальной сборке без
        // этого свойства остаётся пустой строкой, и [ru.cmpas.voice.AppContainer]
        // тогда явно не пытается слать (лог, не попытка с гарантированным 401 —
        // приёмник fail-closed без секрета).
        //
        // ЧЕСТНО ПРО ЭТОТ СЕКРЕТ (G-M3): он попадает сюда как строка и оседает
        // в BuildConfig.ANALYTICS_INGEST_SECRET_MOMENTS внутри APK — а
        // BuildConfig в apk — не тайна: `unzip`/`apktool`/`jadx` любого
        // скачанного APK достают его за минуты, он один и тот же во всех
        // установках. Это НЕ аутентификация устройства и не защита от того,
        // кто целенаправленно ищет секрет в APK, — так делать нельзя, если
        // нужна такая защита. Именно поэтому секрет теперь СВОЙ у МОМЕНТОВ:
        // утёкший из APK ключ позволит слать приёмнику только события МОМЕНТОВ
        // (приёмник сверяет, под каким продуктом разрешён этот секрет), а не
        // приём всех трёх продуктов контура — ущерб от утечки ограничен.
        // Остаётся фильтром случайного постороннего трафика (не угадать
        // URL+секрет наугад), не более. Правильная замена на будущее, когда/если
        // это станет важно: не статический секрет на все инсталляции, а токен
        // на устройство — выданный сервером в обмен на аттестацию (Play
        // Integrity API или аналог), короткоживущий и с ротацией, а не зашитый
        // в бинарник раз и навсегда.
        buildConfigField(
            "String",
            "ANALYTICS_INGEST_SECRET_MOMENTS",
            "\"${project.findProperty("analyticsIngestSecretMoments") ?: ""}\"",
        )
    }

    // Ключа подписи в репозитории НЕТ и не будет. release-сборка здесь выходит
    // НЕПОДПИСАННОЙ; подпись делает CI ключом ИСКЛЮЧИТЕЛЬНО из секретов
    // (см. .github/workflows/android-build.yml): восстановление keystore из
    // секрета, сверка отпечатка с ожидаемым, apksigner sign + verify, отказ при
    // чужом отпечатке и при CN=Android Debug.
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            // Раздаётся людям: НЕ debuggable (по умолчанию для release),
            // minify + shrink включены.
            // Подпись — на стороне CI, ключом из секретов (см. выше).
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
        buildConfig = true // версия приложения в «О приложении» (ТЗ 1.1 §6)
    }

    androidResources {
        // .opus (голос) и .flac (бинаурал) — уже сжатые форматы. Держим их в APK
        // без zip-компрессии, чтобы ExoPlayer/AssetDataSource мог открывать их
        // по FileDescriptor и корректно перематывать (asset:///). .ogg покрыт
        // дефолтным no-compress списком AAPT.
        noCompress += listOf("opus", "flac")
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

    testImplementation("junit:junit:4.13.2")
}
