package com.ruos.journal.prompts

import android.content.Context
import android.provider.CalendarContract
import android.provider.MediaStore
import com.ruos.journal.model.JournalStore
import java.util.Calendar

/**
 * Generates journal prompts entirely on-device from signals already on the phone — no
 * data leaves the device and there is no network call.
 *
 * HONEST NOTE: this is heuristic/template generation, not a language model. It reads
 * real signals (photos taken yesterday via MediaStore; yesterday's calendar events
 * via CalendarContract) and fills Russian templates, plus rotating reflective
 * prompts. Music/steps/weather prompts are stubbed until those providers are wired.
 */
class PromptEngine(private val context: Context) {

    data class Prompt(val kind: String, val text: String)

    fun generate(store: JournalStore): List<Prompt> {
        val out = ArrayList<Prompt>()

        if (store.suggestionEnabled("photos")) photoPrompt()?.let { out.add(it) }
        if (store.suggestionEnabled("calendar")) calendarPrompt()?.let { out.add(it) }
        if (store.suggestionEnabled("reflect")) out.add(reflectPrompt())

        return out.take(3)
    }

    private fun photoPrompt(): Prompt? {
        val (start, end) = yesterdayRange()
        val count = runCatching {
            val sel = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?"
            val args = arrayOf((start / 1000).toString(), (end / 1000).toString())
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID), sel, args, null)?.use { it.count } ?: 0
        }.getOrDefault(0)
        if (count <= 0) return null
        val text = if (count == 1) "Вы сделали фото вчера — что произошло?"
        else "Вы сделали $count фото вчера — расскажите об этом дне."
        return Prompt("photos", text)
    }

    private fun calendarPrompt(): Prompt? {
        val (start, end) = yesterdayRange()
        val title = runCatching {
            val proj = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
            val sel = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, proj,
                sel, arrayOf(start.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC")?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: return null
        return Prompt("calendar", "Вчера у вас была встреча «$title» — как она прошла?")
    }

    private fun reflectPrompt(): Prompt {
        val templates = listOf(
            "Как прошёл ваш день?",
            "За что вы сегодня благодарны?",
            "Что вас сегодня порадовало?",
            "О чём вы сейчас думаете?",
            "Что бы вы хотели запомнить из этого дня?"
        )
        val idx = (System.currentTimeMillis() / 86_400_000L % templates.size).toInt()
        return Prompt("reflect", templates[idx])
    }

    private fun yesterdayRange(): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        return start to (start + 86_400_000L)
    }
}
