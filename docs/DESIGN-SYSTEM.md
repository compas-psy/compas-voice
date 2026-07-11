# Дизайн-система → Kotlin (маппинг токенов)

Источник истины — `design_handoff_kompas_mvp/README.md`. Здесь — как токены
ложатся в код. Реальные значения — в `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`.

## Цвета (→ ui/theme/Color.kt)

| Токен дизайна | Hex | Kotlin |
|---|---|---|
| bg/graphite | `#14171F` | `BgGraphite` — основной фон |
| bg/night | `#0E1014` | `BgNight` — плеер/онбординг/пейволл |
| bg/night-deep | `#0A0C10` | `BgNightDeep` — Дом · ночь |
| bg/player-dim | `#07080B` | `BgPlayerDim` — плеер ночной |
| bg/fade | `#050609` | `BgFade` — сонное угасание |
| surface | `#1C202A` | `Surface` — плитки/карточки |
| surface/alt | `#1A1E27` | `SurfaceAlt` — шит |
| surface/inset | `#12151C` | `SurfaceInset` — треки слайдеров |
| surface/active | `#2A3040` | `SurfaceActive` — активный сегмент |
| surface/night | `#12151D` | `SurfaceNight` |
| accent/terracotta | `#C98A6B` | `Terracotta` — ЕДИНСТВЕННЫЙ акцент действий |
| accent/terracotta-deep | `#B4744F` | `TerracottaDeep` |
| accent/terracotta-muted | `#8B7A6A` | `TerracottaMuted` — ночь |
| logo/green | `#2F6152` | `LogoGreen` |
| secondary/smoky | `#5D6F84` | `Smoky` |
| secondary/indigo | `#4C5678` | `Indigo` — фон практик про сон |
| text/primary | `#F1EEE8` | `TextPrimary` |
| text/primary-night | `#E6E3DC` | `TextPrimaryNight` |
| text/secondary | `#9AA0AB` | `TextSecondary` |
| text/tertiary | `#7F8590` | `TextTertiary` |

**Акцент действий — только terracotta.** Кнопки «Начать/Готово/CTA», активный
таб, активная точка онбординга, галочки. Ничего другого акцентом не красим.

**Фоны практик** — абстрактные радиальные градиентные пятна, оттенок по
состоянию: Сон→indigo, Выход из дня→terracotta, Тревога→green, Разговоры→ochre
`#B4744F`, Опора→green. Никаких иллюстраций/людей/лотосов.

## Типографика (→ ui/theme/Type.kt), системный шрифт

| Роль | Размер/вес |
|---|---|
| Large Title («Что сейчас?») | 33 / Bold |
| Заголовок плеера | 30 / SemiBold |
| Заголовок онбординга | 31 / SemiBold |
| Заголовок экрана (шит/итог) | 24–28 / SemiBold |
| Заголовок группы каталога | 18 / SemiBold |
| Название плитки/карточки | 17 / SemiBold |
| Body / описание | 15–16 / Regular |
| Подпись состояния | 12 / Regular |
| Eyebrow (СЕКЦИЯ) | 12 / SemiBold / letter-spacing .12–.14em / UPPERCASE |
| Таб-бар подпись | 11 / Medium–SemiBold |

Длинные названия-сцены живут в 2 строки. Полная поддержка Dynamic Type — не
хардкодить там, где можно масштабировать (используем `sp`).

## Форма и отступы (→ ui/theme/Shape.kt, Dimens)

- Горизонтальные паддинги экрана: 26dp.
- Скругления: плитки/карточки 22dp; крупные CTA/поля 18dp; шит сверху 30dp;
  чипы/сегменты 11–14dp; пилюли 20dp.
- Тач-цели ≥48dp.
- Тень карточек мягкая; крупная тень только у шита сверху.
- Блюр таб-бара/оверлеев — аппроксимация: полупрозрачная поверхность + граница
  (настоящего backdrop-blur в Compose нет; при желании — библиотека Haze).

## Дышащий фон (→ ui/components/BreathingBackground.kt)

Мягкие размытые радиальные пятна, цикл **10с**: 4с расширение (scale ~0.78→1.14,
opacity ~0.45→0.85), 6с сжатие; `ease-in-out`, бесконечно. Плюс медленный drift.
На плеере 2–3 слоя (terracotta + smoky + green) + затемняющий вертикальный
градиент поверх для читаемости. На паузе — анимация остановлена.
