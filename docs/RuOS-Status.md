# RuOS — сводный статус

Android 14 (AOSP, Pixel 7 «panther»), оформленный под iOS 18, для российского рынка.
Тёмная тема по умолчанию, Golos Text, русский язык, пружинные анимации, тактильный
отклик, **все иконки/глифы рисуются (без эмодзи)**. Аудит: 234 Kotlin-файла, 0
пиктографических эмодзи; все приложения подключены в `vendor/ruos/ruos.mk`; все
кросс-ссылки между приложениями RuOS разрешаются.

## Приложения (`packages/apps/RuOS*`)

| Приложение | Назначение | Док |
|---|---|---|
| RuOSLauncher | Домашний экран, папки, поиск, App Library, Today View | AppLibraryTodayView.md |
| RuOSSettings | Настройки (поиск, экран, аккумулятор, хранилище, доступность, приватность, связь) | Privacy/Connectivity/QrSharing/BatteryStorage/Accessibility.md |
| RuOSKeyboard | Системная клавиатура (ЙЦУКЕН + QWERTY) | — |
| RuOSNotify | Уведомления: баннеры, бейджи, звуки | — |
| RuOSAuth | Биометрия, код-пароль, экран блокировки | — |
| RuOSKeychain | Менеджер паролей + автозаполнение + TOTP | Keychain.md |
| RuOSFindMy | Найти устройство (SMS: звук/блок/стереть/карта) | FindMy.md |
| RuOSEmergency | Экстренный вызов SOS + Медкарта (экран блокировки) | Emergency.md |
| RuOSShare | AirDrop по Wi-Fi Direct | Share.md |
| RuOSReminders | Напоминания (время + геозоны) | Reminders.md |
| RuOSCalendar | Календарь (CalendarContract) | Calendar.md |
| RuOSClock / RuOSAlarm | Часы, будильник, таймер, сон | — |
| RuOSJournal | Зашифрованный дневник | LiveActivities.md |
| RuOSFocus | Режимы фокусирования | FocusModes.md |
| RuOSStandby | Режим ожидания (StandBy) | — |
| RuOSScreenshot / RuOSScreenRecord | Снимок+разметка / запись экрана | Screenshots.md, ScreenRecording.md |
| RuOSAssist | AssistiveTouch (плавающая кнопка) | AssistiveTouch.md |
| RuOSTextActions | «Найти»/«Перевести» в выделении текста | TextActions.md |
| RuOSPhone, RuOSContacts, RuOSMessages, RuOSMail, RuOSBrowser, RuOSMusic, RuOSMaps, RuOSWeather, RuOSCamera, RuOSGallery, RuOSFiles, RuOSNotes, RuOSCalculator, RuOSHealth | Базовые приложения | — |

## SystemUI (`frameworks/base/packages/SystemUI/ruos-src/`)

Динамический остров, Центр управления/уведомлений, жесты, статус-бар, экран блокировки;
запускаются из `RuOSSystemUIModule`: Live Activities, HUD громкости/яркости,
предупреждение о низком заряде, глобальный скриншот. Складываются в сборку SystemUI
скриптом `tools/integrate_into_aosp.sh`.

## Визуальная идентичность (`vendor/ruos/branding/`)

Единый дизайн: 12 фирменных иконок (SVG + VectorDrawable из одного генератора),
курсивный триколорный логотип, boot/shutdown анимации, экран зарядки/«разряжен»,
предупреждение о низком заряде. См. `VisualIdentity.md`, `branding/DESIGN.md`.

## Базовые функции ОС

Полный статус ~50 «table-stakes» функций — в `BaselineFeatures-Roadmap.md`
(почти всё 🆕/✅). Реализовано в этой серии: поиск в настройках, HUD громкости/яркости,
скриншоты+разметка, запись экрана, авто-поворот/яркость/Night Shift/тема, Keychain+TOTP,
Find My, напоминания, календарь, App Library/Today View, доступность, RuOS Share,
SOS+Медкарта, AssistiveTouch, NFC-оплата, трансляция, Wi-Fi/Hotspot QR (с проверённым
QR-кодером), менеджер разрешений, действия с текстом.

## Честный объём (важно)

Это **оверлей-репозиторий** поверх AOSP, не запускавшийся на устройстве в этой сессии.
- **real** — обычный код приложений/настроек (компилируется и работает на эмуляторе/устройстве).
- **[device]** — платформенно-связанное (SurfaceControl-жесты, Wi-Fi Direct, захват
  экрана, биометрия, чтение sysfs/скрытых API через рефлексию, device-admin, FGS):
  математика/логика финальны, но требуют проверки на panther и устранения SELinux-denials.
- **framework-needed** — требует правок фреймворка (полный AOD на keyguard, подавление
  штатного volume-диалога, аппаратный chord для SOS) — задокументировано в соответствующих
  docs и `integrate_into_aosp.sh`.
- **Не реализовано (честно)**: офлайн-перевод/диктовка (нужен движок), оптимизация
  зарядки (нужен демон), ATT-трекинг, облачная карта Find My, межустройственная
  синхронизация. Ничего из перечисленного не имитируется.

## Сборка

`tools/integrate_into_aosp.sh` связывает `vendor/ruos`, `device/ruos`, `packages/apps/RuOS*`
и `ruos-src` с деревом AOSP; затем `lunch` + `make`. Подробности и device-заметки — в
самом скрипте.
