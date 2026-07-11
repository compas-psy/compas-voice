# КОМПАС — Android иконка (крупное дерево)

Adaptive-иконка: дерево вписано в safe-zone (~62% канвы), кольца/лишние поля убраны — знак крупный и читается даже под круглой маской.

## Установка (res/)
- `mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml` → взять `ic_launcher.xml` отсюда.
- `drawable/ic_launcher_foreground.png` (432×432) — дерево, прозрачный фон.
- `drawable/ic_launcher_background.png` (432×432, графитовый градиент) ИЛИ сплошной цвет из `ic_launcher_background_color.xml` (#14171F) — на выбор (градиент богаче, цвет легче).
- `drawable/ic_launcher_monochrome.png` — для тем-иконок Android 13+.
- Легаси-растр (устройства до adaptive): `mipmap/ic_launcher_{48,72,96,144,192}.png` → в mipmap-{m,h,xh,xxh,xxxh}dpi.

## Play Store
- `ic_launcher-playstore.png` (512×512) — карточка в Google Play (полная композиция, углы квадратные, Store скругляет сам).

## Заметки
- Дерево большое специально: под масками (круг/сквиркл/капля) знак не теряется.
- Свечение оставлено едва заметным для глубины; в тёмном лаунчере не мешает.
- Тот же вектор `kompas-tree.svg`, что и в iOS — единый бренд-знак экосистемы.
