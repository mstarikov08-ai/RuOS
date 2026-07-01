package com.ruos.keyboard

object Dictionary {

    // ── Russian frequency list (top ~400 common words) ────────────────────────
    private val RU_WORDS = setOf(
        "и","в","не","он","на","я","что","тот","быть","с","а","весь","это","как","она",
        "по","но","они","к","у","ты","из","мы","за","вы","так","же","от","сказать","этот",
        "который","мочь","человек","о","один","ещё","бы","такой","только","себя","своё",
        "какой","когда","уже","для","вот","кто","да","говорить","его","знать","стать",
        "год","большой","два","наш","свой","до","другой","хотеть","дело","новый","жить",
        "должный","смотреть","почему","потому","сторона","просто","самый","рука","идти",
        "если","нет","день","работа","первый","последний","время","очень","делать","ни",
        "нас","хорошо","жизнь","место","где","там","тут","слово","может","думать","народ",
        "страна","иметь","хотеть","читать","видеть","много","знать","путь","стоять",
        "войти","ребёнок","спасибо","пожалуйста","привет","здравствуйте","извините",
        "конечно","понятно","интересно","хорошо","отлично","плохо","молодец","люди",
        "семья","мама","папа","брат","сестра","друг","подруга","любовь","дом","город",
        "школа","работа","деньги","время","Россия","Москва","номер","телефон","адрес",
        "машина","еда","вода","кофе","чай","хлеб","мясо","рыба","суп","салат","сок",
        "пить","есть","купить","продать","найти","открыть","закрыть","помочь","сказать",
        "думать","понять","спать","встать","сидеть","ходить","бежать","ждать","начать",
        "кончить","написать","прочитать","позвонить","ответить","спросить","ответ","вопрос",
        "проблема","решение","результат","история","новость","сообщение","письмо","текст",
        "слово","предложение","язык","русский","английский","перевод","словарь","книга",
        "статья","сайт","приложение","программа","компьютер","телефон","интернет","сеть",
        "доброе","утро","добрый","день","добрый","вечер","ночь","спокойной","пока","до",
        "свидания","удачи","спасибо","пожалуйста","извини","прости","ничего","всё"
    )

    // ── English frequency list (top ~400 common words) ─────────────────────────
    private val EN_WORDS = setOf(
        "the","be","to","of","and","a","in","that","have","it","for","not","on","with",
        "he","as","you","do","at","this","but","his","by","from","they","we","say","her",
        "she","or","an","will","my","one","all","would","there","their","what","so","up",
        "out","if","about","who","get","which","go","me","when","make","can","like","time",
        "no","just","him","know","take","people","into","year","your","good","some","could",
        "them","see","other","than","then","now","look","only","come","its","over","think",
        "also","back","after","use","two","how","our","work","first","well","way","even",
        "new","want","because","any","these","give","day","most","us","great","between",
        "need","large","often","hand","high","place","hold","without","second","later","run",
        "important","until","children","side","feet","car","mile","night","walk","white",
        "sea","began","grow","took","river","four","carry","state","once","book","hear",
        "stop","without","second","enough","something","nothing","everything","everyone",
        "hello","please","thank","sorry","welcome","okay","yes","no","help","call","text",
        "message","send","open","close","save","find","search","home","back","next","done",
        "cancel","delete","settings","password","email","phone","address","name","number",
        "water","coffee","tea","food","lunch","dinner","breakfast","money","price","today",
        "tomorrow","yesterday","morning","evening","night","week","month","never","always",
        "already","still","again","here","there","where","when","why","how","what","who",
        "which","both","either","every","each","few","more","most","other","some","such",
        "than","that","the","their","then","these","they","thing","think","those","though",
        "three","through","time","together","under","upon","very","while","with","would","yet"
    )

    enum class Lang { RU, EN, MIXED }

    fun suggest(prefix: String, lang: Lang): List<String> {
        if (prefix.isBlank()) return emptyList()
        val p = prefix.lowercase().trim()
        val pool = when (lang) {
            Lang.RU    -> RU_WORDS
            Lang.EN    -> EN_WORDS
            Lang.MIXED -> RU_WORDS + EN_WORDS
        }
        // Exact prefix match, then by length
        val matches = pool.filter { it.startsWith(p) && it != p }
            .sortedBy { it.length }
            .take(3)
        // If fewer than 3, pad with longer prefix completions
        return if (matches.size >= 3) matches
        else matches + pool.filter { it.startsWith(p) }.sortedByDescending { it.length }.take(3 - matches.size)
    }

    fun autocorrect(word: String, lang: Lang): String? {
        if (word.length < 3) return null
        val w = word.lowercase()
        val pool = when (lang) {
            Lang.RU    -> RU_WORDS
            Lang.EN    -> EN_WORDS
            Lang.MIXED -> RU_WORDS + EN_WORDS
        }
        if (pool.contains(w)) return null  // already correct
        // Simple Levenshtein-based nearest neighbour (only for short words to stay fast)
        if (w.length > 10) return null
        return pool
            .filter { kotlin.math.abs(it.length - w.length) <= 2 }
            .minByOrNull { levenshtein(it, w) }
            ?.takeIf { levenshtein(it, w) <= 2 }
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
            else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[m][n]
    }

    // ── Swipe-to-type word matching ─────────────────────────────────────────

    fun matchSwipe(keySequence: List<String>, lang: Lang): List<String> {
        // Drop empty labels (spacer/special keys crossed mid-swipe) BEFORE any seq[0]
        // access — otherwise seq.first()[0] throws StringIndexOutOfBounds and crashes
        // the keyboard during swipe-typing.
        val seq = keySequence.map { it.lowercase() }.filter { it.isNotEmpty() }
        if (seq.size < 2) return emptyList()
        val pool = when (lang) {
            Lang.RU    -> RU_WORDS
            Lang.EN    -> EN_WORDS
            Lang.MIXED -> RU_WORDS + EN_WORDS
        }
        return pool
            .filter { w ->
                w.length >= seq.size - 1 && w.length <= seq.size + 3 &&
                w.first() == seq.first()[0].toString() &&
                w.last() == seq.last()[0].toString()
            }
            .sortedBy { w ->
                // Score: penalise for missing keys in sequence
                val wDedup = w.map { it.toString() }.distinct()
                val seqStr = seq.joinToString("")
                var miss = 0
                for (k in seq) { if (!w.contains(k[0])) miss++ }
                miss
            }
            .take(3)
    }
}
