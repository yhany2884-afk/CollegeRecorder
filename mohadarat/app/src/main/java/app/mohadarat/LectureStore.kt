package app.mohadarat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Lecture(
    val id: String,
    val title: String,
    val subject: String,
    val language: String,
    val createdAt: Long,
    val durationMs: Long,
    val transcript: String,
    val summary: String,
    val notes: String,
    val audioName: String,
)

object LectureStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "lectures.json")

    fun audioFile(ctx: Context, name: String) = File(audioDir(ctx), name)

    fun audioDir(ctx: Context) = File(ctx.filesDir, "audio").also { it.mkdirs() }

    fun list(ctx: Context): List<Lecture> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { arr.getJSONObject(it).toLecture() }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(ctx: Context, id: String) = list(ctx).find { it.id == id }

    fun save(ctx: Context, lecture: Lecture) {
        write(ctx, list(ctx).filter { it.id != lecture.id } + lecture)
    }

    fun delete(ctx: Context, lecture: Lecture) {
        audioFile(ctx, lecture.audioName).delete()
        write(ctx, list(ctx).filter { it.id != lecture.id })
    }

    private fun write(ctx: Context, all: List<Lecture>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        file(ctx).writeText(arr.toString())
    }
}

private fun Lecture.toJson() = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("subject", subject)
    .put("language", language)
    .put("createdAt", createdAt)
    .put("durationMs", durationMs)
    .put("transcript", transcript)
    .put("summary", summary)
    .put("notes", notes)
    .put("audioName", audioName)

private fun JSONObject.toLecture() = Lecture(
    id = optString("id", UUID.randomUUID().toString()),
    title = optString("title"),
    subject = optString("subject"),
    language = optString("language", "ar-EG"),
    createdAt = optLong("createdAt"),
    durationMs = optLong("durationMs"),
    transcript = optString("transcript"),
    summary = optString("summary"),
    notes = optString("notes"),
    audioName = optString("audioName"),
)

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fold(value: String) = value.lowercase()
    .replace("أ", "ا")
    .replace("إ", "ا")
    .replace("آ", "ا")
    .replace("ى", "ي")
    .replace("ة", "ه")

fun summarize(text: String): String {
    val clean = text.replace(Regex("\\[[^]]+]"), " ").replace(Regex("\\s+"), " ").trim()
    if (clean.length < 40) return clean
    val sentences = clean.split(Regex("[.!?؟\\n]+")).map { it.trim() }.filter { it.length > 18 }
    if (sentences.isEmpty()) return clean.take(400)
    return sentences.take(4).joinToString(". ")
}
