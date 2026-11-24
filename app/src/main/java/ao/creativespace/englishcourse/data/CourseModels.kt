package ao.creativespace.englishcourse.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class LessonSection(val title: String, val body: String)
internal data class CourseExample(val english: String, val portuguese: String, val note: String)
internal data class VocabularyItem(val term: String, val meaning: String, val note: String = "")
internal data class QuizItem(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)
internal data class CommonMistake(val incorrect: String, val correction: String, val explanation: String)

internal data class Lesson(
    val id: String,
    val category: String,
    val level: String,
    val minutes: Int,
    val title: String,
    val description: String,
    val sections: List<LessonSection>,
    val examples: List<CourseExample>,
    val tip: String,
    val quiz: List<QuizItem>,
    val vocabulary: List<VocabularyItem>,
    val commonMistakes: List<CommonMistake>
)

internal data class Reading(
    val id: String,
    val level: String,
    val length: String,
    val minutes: Int,
    val title: String,
    val summary: String,
    val paragraphs: List<String>,
    val portugueseText: String,
    val vocabulary: List<VocabularyItem>,
    val questions: List<QuizItem>
)

internal data class DictionaryEntry(
    val term: String,
    val translations: List<String>,
    val sense: String = "",
    val source: String = "WikDict"
)

internal data class CourseBundle(
    val lessons: List<Lesson>,
    val readings: List<Reading>,
    val vocabulary: List<VocabularyItem>
)

internal object CourseData {
    fun load(context: Context): CourseBundle {
        val lessons = parseLessons(readAsset(context, "course/lessons.json"))
        val readings = parseReadings(readAsset(context, "course/readings.json"))
        val baseVocabulary = parseVocabulary(readAsset(context, "course/vocabulary.json"))
        // Index the general glossary plus lesson/readings vocab and bilingual examples.
        val expandedVocabulary = buildList {
            addAll(baseVocabulary)
            lessons.forEach { lesson ->
                addAll(lesson.vocabulary)
                if (lesson.id == "regular-irregular") {
                    lesson.examples.forEach { row ->
                        val base = row.english.trim()
                        val past = row.portuguese.trim()
                        val meaning = row.note.trim()
                        if (base.isNotBlank() && meaning.isNotBlank()) {
                            add(VocabularyItem(base, meaning, "Verbo regular/irregular · Past simple: $past"))
                            past.split('/').map { it.trim() }.filter { it.isNotBlank() }.forEach { form ->
                                add(VocabularyItem(form, meaning, "Forma passada de $base"))
                            }
                        }
                    }
                } else if (lesson.id.startsWith("irregular-verbs")) {
                    lesson.examples.forEach { row ->
                        val base = row.english.trim()
                        val past = row.portuguese.trim()
                        val participleAndMeaning = row.note.trim()
                        val participle = participleAndMeaning.substringBefore('—').trim()
                        val meaning = participleAndMeaning.substringAfter('—', "").trim()
                        if (base.isNotBlank() && meaning.isNotBlank()) {
                            val formNote = "Verbo irregular · forma base: $base · Past simple: $past · Past participle: $participle"
                            val forms = (listOf(base) + past.split('/') + participle.split('/'))
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            forms.forEach { form ->
                                add(VocabularyItem(form, meaning, formNote))
                            }
                        }
                    }
                } else {
                    lesson.examples.forEach { example ->
                        if (example.english.isNotBlank() && example.portuguese.isNotBlank()) {
                            add(VocabularyItem(example.english, example.portuguese, example.note))
                        }
                    }
                }
            }
            readings.forEach { reading -> addAll(reading.vocabulary) }
        }.filter { it.term.isNotBlank() && it.meaning.isNotBlank() }
            .groupBy { it.term.trim().lowercase(java.util.Locale.ROOT) }
            .values
            .map { matches ->
                val first = matches.first()
                val meanings = matches.flatMap { it.meaning.split(';') }
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val notes = matches.map { it.note.trim() }
                    .filter { it.isNotBlank() }.distinct()
                VocabularyItem(first.term, meanings.joinToString("; "), notes.joinToString(" · "))
            }
        return CourseBundle(lessons, readings, expandedVocabulary)
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parseLessons(source: String): List<Lesson> {
        val array = JSONArray(source)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    Lesson(
                        id = item.optString("id"),
                        category = item.optString("cat", "Geral"),
                        level = item.optString("level", "A1"),
                        minutes = item.optInt("time", 10),
                        title = item.optString("title"),
                        description = item.optString("desc"),
                        sections = parseSections(item.optJSONArray("sections")),
                        examples = parseExamples(item.optJSONArray("examples")),
                        tip = item.optString("tip"),
                        quiz = parseQuiz(item.optJSONArray("quiz")),
                        vocabulary = parseVocabularyRows(item.optJSONArray("vocabulary")),
                        commonMistakes = parseMistakes(item.optJSONArray("commonMistakes"))
                    )
                )
            }
        }
    }

    private fun parseReadings(source: String): List<Reading> {
        val array = JSONArray(source)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    Reading(
                        id = item.optString("id"),
                        level = item.optString("level", "A1"),
                        length = item.optString("length", "Curta"),
                        minutes = item.optInt("time", 5),
                        title = item.optString("title"),
                        summary = item.optString("summary"),
                        paragraphs = item.optJSONArray("paragraphs").toStringList(),
                        portugueseText = item.optString("pt"),
                        vocabulary = parseVocabularyRows(item.optJSONArray("vocabulary")),
                        questions = parseQuiz(item.optJSONArray("questions"))
                    )
                )
            }
        }
    }

    private fun parseSections(array: JSONArray?): List<LessonSection> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                add(LessonSection(row.optString(0), row.optString(1)))
            }
        }
    }

    private fun parseExamples(array: JSONArray?): List<CourseExample> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                add(CourseExample(row.optString(0), row.optString(1), row.optString(2)))
            }
        }
    }

    private fun parseVocabularyRows(array: JSONArray?): List<VocabularyItem> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                add(VocabularyItem(row.optString(0), row.optString(1), row.optString(2)))
            }
        }
    }

    private fun parseVocabulary(source: String): List<VocabularyItem> {
        val array = JSONArray(source)
        return parseVocabularyRows(array)
    }

    private fun parseQuiz(array: JSONArray?): List<QuizItem> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                add(
                    QuizItem(
                        question = row.optString(0),
                        options = row.optJSONArray(1).toStringList(),
                        answerIndex = row.optInt(2, 0),
                        explanation = row.optString(3)
                    )
                )
            }
        }
    }

    private fun parseMistakes(array: JSONArray?): List<CommonMistake> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                add(CommonMistake(row.optString(0), row.optString(1), row.optString(2)))
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) add(optString(i))
        }
    }
}
