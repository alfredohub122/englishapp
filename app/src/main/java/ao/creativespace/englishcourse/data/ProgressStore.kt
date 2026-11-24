package ao.creativespace.englishcourse.data

import android.content.Context

internal class ProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("english_course_progress", Context.MODE_PRIVATE)

    fun isLessonComplete(id: String): Boolean = preferences.getBoolean("lesson_$id", false)
    fun isReadingComplete(id: String): Boolean = preferences.getBoolean("reading_$id", false)

    fun markLessonComplete(id: String) {
        preferences.edit().putBoolean("lesson_$id", true).apply()
    }

    fun markReadingComplete(id: String) {
        preferences.edit().putBoolean("reading_$id", true).apply()
    }

    fun completedLessonCount(): Int = preferences.all.count { (key, value) ->
        key.startsWith("lesson_") && value == true
    }

    fun completedReadingCount(): Int = preferences.all.count { (key, value) ->
        key.startsWith("reading_") && value == true
    }
}
