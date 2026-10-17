package com.collegerecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {

    @Query("SELECT * FROM lectures ORDER BY createdAt DESC")
    fun getAllLectures(): Flow<List<Lecture>>

    @Query("SELECT * FROM lectures WHERE id = :id")
    suspend fun getLectureById(id: Long): Lecture?

    @Query("SELECT * FROM lectures WHERE subject = :subject ORDER BY createdAt DESC")
    fun getLecturesBySubject(subject: String): Flow<List<Lecture>>

    @Query("SELECT DISTINCT subject FROM lectures ORDER BY subject ASC")
    fun getAllSubjects(): Flow<List<String>>

    @Query("""
        SELECT * FROM lectures 
        WHERE title LIKE '%' || :query || '%' 
           OR notes LIKE '%' || :query || '%'
           OR subject LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchLectures(query: String): Flow<List<Lecture>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lecture: Lecture): Long

    @Update
    suspend fun update(lecture: Lecture)

    @Delete
    suspend fun delete(lecture: Lecture)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM lectures")
    suspend fun getCount(): Int
}
