package com.collegerecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "lectures")
@TypeConverters(Converters::class)
data class Lecture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String = "عام",
    val audioPath: String,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val language: String = "ar",
    val transcript: List<TranscriptSegment> = emptyList(),
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val notes: String = "",
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList()
)

data class TranscriptSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val speaker: String? = null,
    val confidence: Float = 1.0f
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTranscriptList(value: List<TranscriptSegment>): String = gson.toJson(value)

    @TypeConverter
    fun toTranscriptList(value: String): List<TranscriptSegment> {
        val type = object : TypeToken<List<TranscriptSegment>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
