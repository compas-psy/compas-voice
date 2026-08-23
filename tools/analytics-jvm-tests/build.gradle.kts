// Прогон юнит-тестов аналитики МОМЕНТОВ БЕЗ Android SDK.
//
// Зачем: полноценная сборка `:app:testDebugUnitTest` тянет Android Gradle
// Plugin и платформу с dl.google.com. В агентной среде этот хост закрыт
// egress-политикой (403), поэтому обычный путь там недоступен. Пакет
// ru.cmpas.voice.analytics — чистый JVM (java.time + kotlinx, без единого
// импорта Android), поэтому его тесты компилируются и гоняются обычным
// Kotlin/JVM без AGP: все зависимости берутся с Maven Central.
//
// ВАЖНО про покрытие: этот проект компилирует ТОЛЬКО пакет analytics и его
// тесты. Он НЕ компилирует Android-склейку (KompasApp.kt/AppContainer,
// LocalStore.kt, UI) — её проверяет только настоящая сборка в CI (там SDK
// доступен). Держите пакет analytics чистым от Android-импортов, иначе этот
// быстрый прогон перестанет собираться.
//
// Запуск:  cd tools/analytics-jvm-tests && gradle test
// (нужен Gradle 8.x и JDK 17+; Maven Central должен быть доступен.)

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21") }
}

apply(plugin = "org.jetbrains.kotlin.jvm")

repositories { mavenCentral() }

// Реальные исходники и тесты — без копий, чтобы не расходились с продовым кодом.
configure<KotlinJvmProjectExtension> {
    sourceSets["main"].kotlin.setSrcDirs(listOf("../../android/app/src/main/java/ru/cmpas/voice/analytics"))
    sourceSets["test"].kotlin.setSrcDirs(listOf("../../android/app/src/test/java/ru/cmpas/voice/analytics"))
}

dependencies {
    "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    "testImplementation"("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "skipped", "failed") }
}
