# Проверка чистой логики RuOS

Скрипты-порты Kotlin-логики, которые **проверяют корректность** против эталонов и
тест-векторов. Их можно гонять в CI: ненулевой код возврата = расхождение.

| Скрипт | Что проверяет | Эталон |
|---|---|---|
| `verify_totp.py` | `Totp.code` / `base32Decode` | RFC 6238 Appendix B, RFC 4648 |
| `verify_logic.py` | `FocusSchedule.contains`, `Totp.parseUri`, `QrCodes.wifiPayload` | таблицы истинности |
| `verify_assets.py` | boot/shutdown `.zip` (STORED-кадры, `desc.txt`), charger-фильмстрип, WAV (не тишина) | формат AOSP |
| `verify_stores.py` | round-trip `FocusStore` / `ReminderStore` / `KeychainStore` (serialize↔parse) | равенство объекта |
| `../../branding/gen_branding.py` + `preview.py` | иконки/логотип | визуальный контактный лист |

QR-кодер (`RuOSSettings/.../util/QrEncoder.kt`) проверялся **побитово против библиотеки
`qrcode`** (см. историю: 34/34 совпадений, v1–v10, кириллица, WIFI-строки) — так были
найдены и исправлены реальные ошибки (сдвиг в Reed-Solomon, пропуск выравнивания на
линии синхронизации). Для повторной проверки: `pip install qrcode` и сравнить
`QrEncoder.encode` (порт) с `qrcode` при `mask_pattern=0, ERROR_CORRECT_M`.

## Запуск

```sh
python3 vendor/ruos/tools/verify/verify_totp.py
python3 vendor/ruos/tools/verify/verify_logic.py
python3 vendor/ruos/tools/verify/verify_assets.py
python3 vendor/ruos/tools/verify/verify_stores.py
```

Найденные ошибки исправляются в Kotlin (например, `Totp.parseUri` не декодировал
URL-кодированный параметр `issuer` — кириллический эмитент «Госуслуги» показывался как
`%D0%93…`; исправлено).
