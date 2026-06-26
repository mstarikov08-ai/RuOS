package com.ruos.keyboard

// Special key codes (negative so they don't clash with Unicode codepoints)
const val CODE_SHIFT        = -1
const val CODE_DELETE       = -2
const val CODE_SWITCH_LANG  = -3
const val CODE_NUMBERS      = -4
const val CODE_SYMBOLS      = -5
const val CODE_SPACE        = -6
const val CODE_RETURN       = -7
const val CODE_BACK_ALPHA   = -8
const val CODE_BACK_NUMBERS = -9

enum class KeyStyle { LETTER, ACTION, SPACE, RETURN, GLOBE }

data class Key(
    val label: String,
    val code: Int,
    val altLabel: String = "",
    val alternatives: List<String> = emptyList(),
    val widthWeight: Float = 1f,
    val style: KeyStyle = KeyStyle.LETTER
)

data class KeyRow(val keys: List<Key>)
data class KeyboardMode(val rows: List<KeyRow>, val name: String)

object Layouts {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun letter(ch: String, vararg alts: String) = Key(
        label = ch,
        code = ch[0].code,
        alternatives = alts.toList()
    )

    private fun action(label: String, code: Int, weight: Float = 1.5f) = Key(
        label = label, code = code, widthWeight = weight, style = KeyStyle.ACTION
    )

    // ── Russian ЙЦУКЕН ───────────────────────────────────────────────────────

    val RUSSIAN = KeyboardMode(
        name = "ru",
        rows = listOf(
            KeyRow(listOf(
                letter("й"), letter("ц"), letter("у"), letter("к"),
                letter("е", "ё"), letter("н"), letter("г"),
                letter("ш"), letter("щ"), letter("з"), letter("х", "ъ")
            )),
            KeyRow(listOf(
                letter("ф"), letter("ы"), letter("в"), letter("а"),
                letter("п"), letter("р"), letter("о"), letter("л"),
                letter("д"), letter("ж"), letter("э")
            )),
            KeyRow(listOf(
                action("shift", CODE_SHIFT),
                letter("я"), letter("ч"), letter("с"), letter("м"),
                letter("и"), letter("т"), letter("ь"), letter("б"), letter("ю"),
                action("del", CODE_DELETE)
            )),
            KeyRow(listOf(
                Key("globe",  CODE_SWITCH_LANG, style = KeyStyle.GLOBE, widthWeight = 1.0f),
                action("123", CODE_NUMBERS, 1.2f),
                Key("Пробел", CODE_SPACE, style = KeyStyle.SPACE, widthWeight = 7.0f),
                Key("Ввод",   CODE_RETURN, style = KeyStyle.RETURN, widthWeight = 1.8f)
            ))
        )
    )

    // ── English QWERTY ───────────────────────────────────────────────────────

    val ENGLISH = KeyboardMode(
        name = "en",
        rows = listOf(
            KeyRow(listOf(
                letter("q"), letter("w"),
                letter("e","è","é","ê","ë"),
                letter("r"), letter("t"),
                letter("y","ÿ"),
                letter("u","ù","ú","û","ü"),
                letter("i","ì","í","î","ï"),
                letter("o","ò","ó","ô","ö","ø"),
                letter("p")
            )),
            KeyRow(listOf(
                letter("a","à","á","â","ä","æ"),
                letter("s","ß"),
                letter("d"), letter("f"), letter("g"), letter("h"),
                letter("j"), letter("k"), letter("l")
            )),
            KeyRow(listOf(
                action("shift", CODE_SHIFT),
                letter("z"), letter("x"),
                letter("c","ç"),
                letter("v"), letter("b"),
                letter("n","ñ"),
                letter("m"),
                action("del", CODE_DELETE)
            )),
            KeyRow(listOf(
                Key("globe",  CODE_SWITCH_LANG, style = KeyStyle.GLOBE, widthWeight = 1.0f),
                action("123", CODE_NUMBERS, 1.2f),
                Key("space",  CODE_SPACE, style = KeyStyle.SPACE, widthWeight = 6.8f),
                Key("return", CODE_RETURN, style = KeyStyle.RETURN, widthWeight = 1.8f)
            ))
        )
    )

    // ── Numbers ──────────────────────────────────────────────────────────────

    val NUMBERS = KeyboardMode(
        name = "num",
        rows = listOf(
            KeyRow(listOf(
                letter("1","!"),  letter("2","@"),  letter("3","#"),
                letter("4","$"),  letter("5","%"),  letter("6","^"),
                letter("7","&"),  letter("8","*"),  letter("9","("),  letter("0",")")
            )),
            KeyRow(listOf(
                letter("-","_"),  letter("/","\\"), letter(":",";"),
                letter(";",":"),  letter("(","["),  letter(")","]"),
                letter("$","€"),  letter("&"),       letter("@"),      letter("\"","'")
            )),
            KeyRow(listOf(
                action("#+=", CODE_SYMBOLS, 1.5f),
                letter(".","…"), letter(","),
                letter("?","¿"), letter("!","¡"), letter("'","`"),
                action("del", CODE_DELETE, 1.5f)
            )),
            KeyRow(listOf(
                action("ABC", CODE_BACK_ALPHA, 1.5f),
                Key("space", CODE_SPACE, style = KeyStyle.SPACE, widthWeight = 5.5f),
                Key("return", CODE_RETURN, style = KeyStyle.RETURN, widthWeight = 1.5f)
            ))
        )
    )

    // ── Symbols ──────────────────────────────────────────────────────────────

    val SYMBOLS = KeyboardMode(
        name = "sym",
        rows = listOf(
            KeyRow(listOf(
                letter("["), letter("]"), letter("{"), letter("}"),
                letter("#"), letter("%"), letter("^"), letter("*"),
                letter("+"), letter("=")
            )),
            KeyRow(listOf(
                letter("_"), letter("\\"), letter("|"), letter("~"),
                letter("<"), letter(">"),  letter("€"), letter("£"),
                letter("¥"), letter("•")
            )),
            KeyRow(listOf(
                action("123", CODE_BACK_NUMBERS, 1.5f),
                letter("."), letter(","),
                letter("?"), letter("!"), letter("'"),
                action("del", CODE_DELETE, 1.5f)
            )),
            KeyRow(listOf(
                action("ABC", CODE_BACK_ALPHA, 1.5f),
                Key("space", CODE_SPACE, style = KeyStyle.SPACE, widthWeight = 5.5f),
                Key("return", CODE_RETURN, style = KeyStyle.RETURN, widthWeight = 1.5f)
            ))
        )
    )
}
