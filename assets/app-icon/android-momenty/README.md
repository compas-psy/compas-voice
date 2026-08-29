# Иконка МОМЕНТОВ (Android) — плоский фон #4A4E78, дерево из семейного вектора

Заменяет прежнюю графитовую иконку со свечением/кольцами (см. `design_handoff_kompas_mvp/android-icon/`,
ТЗ 0.1 «Тише», не обновлялось под текущее имя/направление). Тот же знак дерева, что и внутри
экранов (`android/app/src/main/java/.../ic_tree.xml`) — просто теперь это сама adaptive-иконка,
а не только внутриэкранный лого-знак.

## Источники
- `ic_launcher_background.svg` — сплошная заливка `#4A4E78`, 108×108, без скруглений
  (маску накладывает лаунчер/система).
- `ic_launcher_foreground.svg` — дерево `#EDEBF2` на прозрачном фоне, 108×108, дерево вписано
  в safe-zone через `translate/scale/translate`.
- `ic_launcher_monochrome.svg` — тот же силуэт для тем-иконок Android 13+ (Android перекрашивает
  сам, заливка в источнике не важна).
- `momenty-legacy-512.png` — full-bleed квадрат со скруглёнными углами (пре-Android-8 лаунчеры,
  масштабируется в `mipmap-*/ic_launcher.png`).
- `momenty-round.png` — тот же композит, замаскированный кругом (`mipmap-*/ic_launcher_round.png`).
- `momenty-square.png` — референс без даунскейла (512×512).

## Куда легли собранные растры
`android/app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.png` (432×432,
рендер SVG отсюда) + `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png` (масштаб из
`momenty-legacy-512.png`/`momenty-round.png`). `mipmap-anydpi-v26/ic_launcher.xml` не менялся —
он уже ссылается на `@drawable/ic_launcher_{background,foreground,monochrome}` по имени.

## Пересборка растров из SVG
```bash
python3 -c "
import cairosvg
for n in ['ic_launcher_background','ic_launcher_foreground','ic_launcher_monochrome']:
    cairosvg.svg2png(url=f'{n}.svg', write_to=f'{n}.png', output_width=432, output_height=432)
"
```
