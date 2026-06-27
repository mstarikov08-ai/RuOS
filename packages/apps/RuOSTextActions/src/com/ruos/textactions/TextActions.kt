package com.ruos.textactions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.net.URLEncoder

/**
 * System text-selection actions. Each activity registers for ACTION_PROCESS_TEXT, so when
 * the user selects text in ANY app it appears as a toolbar item (like iOS «Найти» /
 * «Перевести»). They have no UI — they read the selection and hand off to the browser /
 * Yandex, then finish. No fake translation: «Перевести» opens Yandex Translate, which does
 * the work.
 */
abstract class ProcessTextActivity : Activity() {

    abstract fun urlFor(text: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = (intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY))?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlFor(text)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    protected fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** «Найти» — web search for the selection. */
class SearchTextActivity : ProcessTextActivity() {
    override fun urlFor(text: String) = "https://yandex.ru/search/?text=${enc(text)}"
}

/** «Перевести» — open Yandex Translate (auto-detect → Russian) with the selection. */
class TranslateTextActivity : ProcessTextActivity() {
    override fun urlFor(text: String) = "https://translate.yandex.ru/?text=${enc(text)}&lang=ru"
}
