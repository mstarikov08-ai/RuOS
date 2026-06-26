package com.ruos.journal.model

/** A single journal entry. Photos are content-URI strings; audio is an app-file path. */
data class JournalEntry(
    val id: Long,
    val timestamp: Long,
    val text: String,
    val photoUris: List<String> = emptyList(),
    val moodId: Int? = null,
    val tags: List<String> = emptyList(),
    val location: String? = null,
    val audioPath: String? = null
) {
    fun firstLine(): String = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
    val mood: Mood? get() = moodId?.let { Mood.byId(it) }
}
