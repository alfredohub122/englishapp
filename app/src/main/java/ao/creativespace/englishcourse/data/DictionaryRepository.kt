package ao.creativespace.englishcourse.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

internal enum class DictionaryDirection(val fileName: String, val sourceLabel: String, val targetLabel: String) {
    ENGLISH_TO_PORTUGUESE("en-pt.sqlite3", "Inglês", "Português"),
    PORTUGUESE_TO_ENGLISH("pt-en.sqlite3", "Português", "Inglês")
}

internal class DictionaryRepository(private val context: Context) {
    fun search(rawQuery: String, direction: DictionaryDirection): List<DictionaryEntry> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()

        val databaseFile = obtainDatabase(direction.fileName) ?: return emptyList()
        return try {
            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                searchGrouped(db, query).ifEmpty { searchSimple(db, query) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun obtainDatabase(name: String): File? {
        val directory = File(context.filesDir, "dictionary").apply { mkdirs() }
        val target = File(directory, name)
        if (target.exists() && isSQLiteDatabase(target)) return target

        val temporary = File(directory, "$name.part")
        return try {
            if (target.exists()) target.delete()
            if (temporary.exists()) temporary.delete()
            context.assets.open("dictionary/$name").use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(isSQLiteDatabase(temporary)) { "Invalid SQLite dictionary asset: $name" }
            check(temporary.renameTo(target)) { "Could not install dictionary database: $name" }
            target
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    private fun isSQLiteDatabase(file: File): Boolean {
        if (!file.isFile || file.length() <= 128L) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                val count = input.read(header)
                count == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
            }
        }.getOrDefault(false)
    }

    private fun searchGrouped(db: SQLiteDatabase, query: String): List<DictionaryEntry> {
        if (!tableExists(db, "translation_grouped")) return emptyList()
        val exact = queryRows(
            db,
            "SELECT written_rep, trans_list, COALESCE(sense_list, '') " +
                "FROM translation_grouped WHERE written_rep = ? COLLATE NOCASE " +
                "ORDER BY score DESC LIMIT 18",
            query
        )
        if (exact.isNotEmpty()) return exact
        return queryRows(
            db,
            "SELECT written_rep, trans_list, COALESCE(sense_list, '') " +
                "FROM translation_grouped WHERE written_rep LIKE ? COLLATE NOCASE " +
                "ORDER BY score DESC LIMIT 18",
            "$query%"
        )
    }

    private fun searchSimple(db: SQLiteDatabase, query: String): List<DictionaryEntry> {
        if (!tableExists(db, "simple_translation")) return emptyList()
        val exact = queryRows(
            db,
            "SELECT written_rep, trans_list, '' FROM simple_translation " +
                "WHERE written_rep = ? COLLATE NOCASE ORDER BY max_score DESC LIMIT 18",
            query
        )
        if (exact.isNotEmpty()) return exact
        return queryRows(
            db,
            "SELECT written_rep, trans_list, '' FROM simple_translation " +
                "WHERE written_rep LIKE ? COLLATE NOCASE ORDER BY max_score DESC LIMIT 18",
            "$query%"
        )
    }

    private fun queryRows(db: SQLiteDatabase, sql: String, argument: String): List<DictionaryEntry> {
        return db.rawQuery(sql, arrayOf(argument)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val term = cursor.getString(0)?.trim().orEmpty()
                    val rawTranslations = cursor.getString(1).orEmpty()
                    val sense = cursor.getString(2).orEmpty()
                    val translations = rawTranslations.split('|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    if (term.isNotEmpty() && translations.isNotEmpty()) {
                        add(DictionaryEntry(term, translations, sense))
                    }
                }
            }
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name = ? LIMIT 1",
            arrayOf(table)
        ).use { it.moveToFirst() }
}
