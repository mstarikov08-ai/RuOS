package com.ruos.backup

/**
 * The contract every RuOS app implements so RuOSBackup can archive it. Apps are sandboxed
 * by UID, so a central reader can't touch their files — instead each app exposes a tiny
 * signature-permission-guarded ContentProvider at authority "<pkg>.backup":
 *
 *   query(content://<pkg>.backup/state)  → 1-row cursor, column "json" = serialised state
 *   call(uri, "import", <json>, null)    → app restores that state
 *
 * The provider is protected by [PERMISSION] (protectionLevel="signature"), so only the
 * platform-signed RuOSBackup can read/write it.
 */
object BackupContract {
    const val PERMISSION = "com.ruos.permission.BACKUP"
    const val PATH = "state"
    const val COLUMN = "json"
    const val METHOD_IMPORT = "import"

    /** Sections RuOSBackup knows how to back up: human label + app package. */
    val SECTIONS: List<Pair<String, String>> = listOf(
        "Связка ключей"    to "com.ruos.keychain",
        "Напоминания"      to "com.ruos.reminders",
        "Фокусирование"    to "com.ruos.focus",
        "Замена текста"    to "com.ruos.keyboard",
        "Виджеты"          to "com.ruos.launcher",
        "Будильники"       to "com.ruos.alarm",
        "Журнал"           to "com.ruos.journal",
        "Экстренное"       to "com.ruos.emergency",
    )

    fun authority(pkg: String) = "$pkg.backup"
}
