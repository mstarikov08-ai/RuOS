package com.ruos.notes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folder: String,
    val deleted: Int
)

class NotesDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "ruos_notes.db"
        const val DB_VERSION = 1
        const val TABLE = "notes"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_BODY = "body"
        const val COL_CREATED = "created_at"
        const val COL_UPDATED = "updated_at"
        const val COL_FOLDER = "folder"
        const val COL_DELETED = "deleted"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL DEFAULT '',
                $COL_BODY TEXT NOT NULL DEFAULT '',
                $COL_CREATED INTEGER NOT NULL,
                $COL_UPDATED INTEGER NOT NULL,
                $COL_FOLDER TEXT NOT NULL DEFAULT 'all',
                $COL_DELETED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun getAllNotes(folder: String = "all"): List<Note> {
        val db = readableDatabase
        val notes = mutableListOf<Note>()
        val cursor = db.query(
            TABLE,
            null,
            "$COL_DELETED = 0 AND ($COL_FOLDER = ? OR ? = 'all')",
            arrayOf(folder, folder),
            null, null,
            "$COL_UPDATED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                notes.add(cursorToNote(it))
            }
        }
        return notes
    }

    fun getNoteById(id: Long): Note? {
        val db = readableDatabase
        val cursor = db.query(TABLE, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) cursorToNote(it) else null
        }
    }

    fun insertNote(title: String, body: String, folder: String = "all"): Long {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_BODY, body)
            put(COL_CREATED, now)
            put(COL_UPDATED, now)
            put(COL_FOLDER, folder)
            put(COL_DELETED, 0)
        }
        return db.insert(TABLE, null, values)
    }

    fun updateNote(id: Long, title: String, body: String): Int {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_BODY, body)
            put(COL_UPDATED, now)
        }
        return db.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteNote(id: Long): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DELETED, 1)
            put(COL_UPDATED, System.currentTimeMillis())
        }
        return db.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun permanentlyDelete(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getDeletedNotes(): List<Note> {
        val db = readableDatabase
        val notes = mutableListOf<Note>()
        val cursor = db.query(
            TABLE, null, "$COL_DELETED = 1",
            null, null, null, "$COL_UPDATED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                notes.add(cursorToNote(it))
            }
        }
        return notes
    }

    fun getCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE $COL_DELETED = 0", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun searchNotes(query: String): List<Note> {
        val db = readableDatabase
        val notes = mutableListOf<Note>()
        val like = "%$query%"
        val cursor = db.query(
            TABLE, null,
            "$COL_DELETED = 0 AND ($COL_TITLE LIKE ? OR $COL_BODY LIKE ?)",
            arrayOf(like, like),
            null, null, "$COL_UPDATED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                notes.add(cursorToNote(it))
            }
        }
        return notes
    }

    private fun cursorToNote(cursor: android.database.Cursor): Note {
        return Note(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)) ?: "",
            body = cursor.getString(cursor.getColumnIndexOrThrow(COL_BODY)) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED)),
            folder = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER)) ?: "all",
            deleted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DELETED))
        )
    }
}
