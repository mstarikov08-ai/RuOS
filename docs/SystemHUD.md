# RuOS системные HUD — громкость и яркость

`RuOSSystemHud` (SystemUI, `ruos-src/.../hud/`) рисует iOS-овый тонкий «капсульный»
индикатор громкости и яркости: матовая капсула выезжает слева, заполняется до нового
уровня и сама исчезает. Глифы (динамик/колокольчик/солнце) нарисованы на canvas, без
эмодзи. Пружинное появление, тактильный тик, авто-скрытие ~1.3 c.

## Что работает (реально)

- **Громкость**: приёмник `android.media.VOLUME_CHANGED_ACTION` + `AudioManager` —
  показывает уровень и mute для медиа/звонка/вызова.
- **Яркость**: `ContentObserver` на `Settings.System.SCREEN_BRIGHTNESS`.
- Подключается из `RuOSSystemUIModule.initDynamicIsland`.

## Чтобы остался ТОЛЬКО RuOS HUD (framework-доработка)

Сейчас при нажатии клавиш громкости стоковый диалог Android тоже может появиться.
Чтобы его подавить, в SystemUI нужно нейтрализовать штатный `VolumeDialog`:

- в `frameworks/base/packages/SystemUI/src/com/android/systemui/volume/`
  `VolumeDialogComponent`/`VolumeDialogImpl` сделать `show()` no-op (или вернуть пустой
  диалог) под RuOS-флагом, оставив обработку клавиш и `setStreamVolume` штатной — RuOS
  HUD реагирует на итоговое изменение громкости.

Это одна точечная правка; сам HUD уже готов и самодостаточен. После — проверить на
устройстве тип окна (`TYPE_APPLICATION_OVERLAY`) и отсутствие avc-denials.
