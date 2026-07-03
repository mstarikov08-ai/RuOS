package com.ruos.phone.spam

/**
 * Pure spam-matching logic shared by call screening and SMS filtering. Given the user's rules and
 * an incoming number (and, for SMS, a message body), it decides whether to block. Kept free of any
 * Android dependency so it can be unit/round-trip-verified (see verify_logic.py). An identical copy
 * lives in RuOSMessages; both are covered by the same reference port.
 *
 * Matching rules, in order:
 *   1. Allow-list (a number the user explicitly trusts) always wins.
 *   2. Exact blocked number.
 *   3. Blocked prefix (e.g. "+7900" — a whole range, or "8800" cold-call codes).
 *   4. For SMS only: a blocked keyword appears in the body (case-insensitive).
 *   5. blockUnknownShort: block 3–5 digit short codes not in contacts (typical spam senders).
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

    /** Normalise a phone number to digits (keeping a leading +), so "+7 (900) 123-45-67" matches. */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder()
        for ((i, c) in raw.trim().withIndex()) {
            if (c == '+' && i == 0) sb.append('+')
            else if (c.isDigit()) sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Decide for a call ([body] null) or an SMS ([body] set). [isContact] tells us whether the
     * number is in the user's contacts (used by the short-code rule).
     */
    fun decide(rules: Rules, rawNumber: String?, body: String?, isContact: Boolean): Decision {
        val num = normalize(rawNumber)
        // 1. Explicit allow always wins.
        if (num.isNotEmpty() && rules.allowedNumbers.any { normalize(it) == num }) return Decision.ALLOW
        // 2. Exact blocked number.
        if (num.isNotEmpty() && rules.blockedNumbers.any { normalize(it) == num }) return Decision.BLOCK
        // 3. Blocked prefix.
        if (num.isNotEmpty() && rules.blockedPrefixes.any { it.isNotEmpty() && num.startsWith(normalize(it)) }) return Decision.BLOCK
        // 4. SMS keyword.
        if (body != null && rules.blockedKeywords.isNotEmpty()) {
            val low = body.lowercase()
            if (rules.blockedKeywords.any { it.isNotBlank() && low.contains(it.lowercase()) }) return Decision.BLOCK
        }
        // 5. Unknown short code.
        if (rules.blockUnknownShort && !isContact) {
            val digits = num.removePrefix("+")
            if (digits.length in 3..5 && digits.all { it.isDigit() }) return Decision.BLOCK
        }
        return Decision.ALLOW
    }

    /**
     * True when [rawNumber] is explicitly blocked (exact number or prefix). Used to hide blocked
     * senders from message lists — deliberately narrower than [decide]: keyword and short-code
     * rules need a message body / contacts context and must not hide whole threads.
     */
    fun isExplicitlyBlocked(rules: Rules, rawNumber: String?): Boolean {
        val num = normalize(rawNumber)
        if (num.isEmpty()) return false
        if (rules.allowedNumbers.any { normalize(it) == num }) return false
        if (rules.blockedNumbers.any { normalize(it) == num }) return true
        return rules.blockedPrefixes.any { it.isNotEmpty() && num.startsWith(normalize(it)) }
    }
}
