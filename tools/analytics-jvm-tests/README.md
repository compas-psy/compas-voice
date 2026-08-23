# analytics-jvm-tests — прогон тестов аналитики без Android SDK

Быстрый Kotlin/JVM-прогон юнит-тестов пакета `ru.cmpas.voice.analytics`
**в обход Android Gradle Plugin**.

## Зачем это нужно

Обычный путь — `./gradlew :app:testDebugUnitTest` в `android/` — тянет Android
Gradle Plugin и платформу SDK с `dl.google.com`. В агентной/облачной среде этот
хост закрыт egress-политикой (HTTP 403), поэтому AGP там не поднимается и тесты
так не запустить.

Пакет `ru.cmpas.voice.analytics` — **чистый JVM**: `AnalyticsSchema.kt`,
`AnalyticsRecorder.kt`, `AnalyticsTransport.kt` импортируют только `java.time`,
`java.util` и `kotlinx.*` (сериализация/корутины), ни одного Android-класса.
Поэтому его тесты компилируются и гоняются обычным Kotlin/JVM, а зависимости
берутся с Maven Central (он в среде доступен).

## Что покрыто

Все тесты пакета — те же файлы, что гоняет CI, без копий (подключены через
`sourceSets` из `../../android/app/src/.../analytics`):
`AnalyticsSchemaTest`, `AnalyticsRecorderTest`, `AnalyticsTransportTest` (+
`…ConfigTest`, `…PendingRevocationTest`, `IngestResponseAcceptedTest`),
`AnalyticsRegistryComplianceTest`, `AnalyticsQueueConsentTest`. Итого **54 теста**.

## Чего этот прогон НЕ проверяет

Только пакет `analytics`. Он **НЕ компилирует Android-склейку** —
`KompasApp.kt`/`AppContainer`, `LocalStore.kt`, весь UI. Их проверяет ТОЛЬКО
настоящая сборка в GitHub Actions (там SDK доступен). Если добавить в пакет
`analytics` файл с Android-импортом, этот быстрый прогон перестанет собираться —
это ожидаемо: держите пакет чистым, склейку проверяйте в CI.

## Запуск

```bash
cd tools/analytics-jvm-tests
gradle test          # нужен Gradle 8.x и JDK 17+, доступ к Maven Central
```

Отчёт — `build/reports/tests/test/index.html`, машинный результат —
`build/test-results/test/*.xml`.
