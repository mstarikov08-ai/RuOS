package com.ruos.messages.spam

/**
 * SMS-side copy of the spam-matching logic. Identical to RuOSPhone's [com.ruos.phone.spam.SpamFilter]
 * (the two apps are separate APKs so the file is duplicated, not shared) and covered by the same
 * reference port in verify_logic.py. Rules are fetched from Phone's signature-guarded spam provider
 * so a number blocked for calls also filters SMS.
 */
object SpamFilter {

    data class Rules(
        val blockedNumbers: Set<String> = emptySet(),
        val blockedPrefixes: List<String> = emptyList(),
        val blockedKeywords: List<String> = emptyList(),
        val allowedNumbers: Set<String> = emptySet(),
        val blockUnknownShort: Boolean = false
    )

    enum class Decision { ALLOW, BLOCK }

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder()
        for ((i, c) in raw.trim().withIndex()) {
            if (c == '+' && i == 0) sb.append('+')
            else if (c.isDigit()) sb.append(c)
        }
        return sb.toString()
    }

    fun decide(rules: Rules, rawNumber: String?, body: String?, isContact: Boolean): Decision {
        val num = normalize(rawNumber)
        if (num.isNotEmpty() && rules.allowedNumbers.any { normalize(it) == num }) return Decision.ALLOW
        if (num.isNotEmpty() && rules.blockedNumbers.any { normalize(it) == num }) return Decision.BLOCK
        if (num.isNotEmpty() && rules.blockedPrefixes.any { it.isNotEmpty() && num.startsWith(normalize(it)) }) return Decision.BLOCK
        if (body != null && rules.blockedKeywords.isNotEmpty()) {
            val low = body.lowercase()
            if (rules.blockedKeywords.any { it.isNotBlank() && low.contains(it.lowercase()) }) return Decision.BLOCK
        }
        if (rules.blockUnknownShort && !isContact) {
            val digits = num.removePrefix("+")
            if (digits.length in 3..5 && digits.all { it.isDigit() }) return Decision.BLOCK
        }
        return Decision.ALLOW
    }

    /** Parse the rules JSON exposed by Phone's spam provider (mirror of SpamStore.fromJson). */
    fun fromJson(raw: String): Rules {
        val o = org.json.JSONObject(raw)
        fun arr(name: String): List<String> {
            val a = o.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        return Rules(
            blockedNumbers = arr("numbers").toSet(),
            blockedPrefixes = arr("prefixes"),
            blockedKeywords = arr("keywords"),
            allowedNumbers = arr("allowed").toSet(),
            blockUnknownShort = o.optBoolean("unknownShort", false)
        )
    }
}
