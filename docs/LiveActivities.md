# Живые события (Live Activities) — API для разработчиков

Живые события показывают актуальную информацию в **Dynamic Island** и на **экране
блокировки** в реальном времени. Это часть RuOS; стороннему приложению **не нужен
SDK** — достаточно отправить системный broadcast в SystemUI.

Одновременно отображается максимум **2** события: остров делится на две «капсулы».

## Протокол

Отправьте явный broadcast в `com.android.systemui`:

| Действие | Назначение |
|---|---|
| `com.ruos.liveactivity.UPDATE` | создать или обновить событие |
| `com.ruos.liveactivity.END` | завершить событие |

### Extras

| Ключ | Тип | Описание |
|---|---|---|
| `id` | String | уникальный стабильный идентификатор (обязательно) |
| `type` | String | `taxi` / `delivery` / `sports` / `payment` / `navigation` / `timer` / `stream` / `generic` |
| `color` | Int (ARGB) | акцентный цвет |
| `compact_leading` | String | короткий текст слева в капсуле |
| `compact_trailing` | String | короткий текст справа (например, «5 мин») |
| `minimal` | String | крошечный текст в режиме разделения |
| `title` | String | заголовок развёрнутой карточки |
| `subtitle` | String | подзаголовок |
| `body` | String | подробности (многострочно) |
| `progress` | Float 0..1 | прогресс (необязательно) |
| `content_intent` | PendingIntent | открыть приложение по нажатию |

Остров показывает: **minimal** (точка), **compact** (капсула с иконкой и ключевой
информацией), **expanded** (карточка по нажатию). На экране блокировки карточка
появляется под уведомлениями; смахивание — закрыть, нажатие — открыть приложение.

## Пример: Яндекс Go (такси)

```kotlin
fun updateTaxi(context: Context, minutes: Int, driver: String, car: String, plate: String) {
    val open = PendingIntent.getActivity(context, 0,
        context.packageManager.getLaunchIntentForPackage("ru.yandex.taxi"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    context.sendBroadcast(Intent("com.ruos.liveactivity.UPDATE").apply {
        setPackage("com.android.systemui")
        putExtra("id", "yandexgo.ride")
        putExtra("type", "taxi")
        putExtra("color", 0xFFFFCC00.toInt())
        putExtra("compact_leading", "Такси")
        putExtra("compact_trailing", "$minutes мин")
        putExtra("minimal", "$minutes")
        putExtra("title", "Машина в пути")
        putExtra("subtitle", "$driver · $car · $plate")
        putExtra("progress", (15 - minutes).coerceIn(0, 15) / 15f)
        putExtra("content_intent", open)
    })
}
// По прибытии:
context.sendBroadcast(Intent("com.ruos.liveactivity.END")
    .setPackage("com.android.systemui").putExtra("id", "yandexgo.ride"))
```

## Пример: Яндекс Еда (доставка)

```kotlin
putExtra("type", "delivery")
putExtra("compact_leading", "Еда")
putExtra("compact_trailing", "12 мин")
putExtra("title", "Заказ готовится")     // → «В пути» → «Рядом» → «Доставлено»
putExtra("subtitle", "Курьер: Иван")
```

## Пример: VK Live (трансляция)

```kotlin
putExtra("type", "stream")
putExtra("color", 0xFFFF3B30.toInt())
putExtra("compact_leading", "LIVE")
putExtra("compact_trailing", "1.2K")     // зрители
putExtra("title", "Прямой эфир: @blogger")
```

## Пример: спортивный счёт

```kotlin
putExtra("type", "sports")
putExtra("compact_leading", "2 : 1")
putExtra("title", "Спартак — Зенит")
putExtra("subtitle", "2-й тайм, 67'")
```

## Встроенный пример в RuOS

RuOS Часы публикует **таймер** как живое событие — см.
`packages/apps/RuOSClock/.../LiveTimer.kt`. Это рабочий референс протокола.

## Замечания

- Сторонние приложения (Яндекс, VK) должны сами вызывать этот API — RuOS
  предоставляет приёмник и отрисовку; примеры выше — готовый код для интеграции.
- Платёжные события MIR Pay и навигацию Яндекс.Карт реализуют соответствующие
  приложения тем же протоколом.
- Режимы фокусировки могут скрывать события; навигация и таймеры показываются всегда.
