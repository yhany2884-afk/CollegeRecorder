package com.collegerecorder.data

import kotlinx.coroutines.flow.Flow

class LectureRepository(private val dao: LectureDao) {

    fun getAllLectures(): Flow<List<Lecture>> = dao.getAllLectures()

    fun getLecturesBySubject(subject: String): Flow<List<Lecture>> =
        dao.getLecturesBySubject(subject)

    fun getAllSubjects(): Flow<List<String>> = dao.getAllSubjects()

    fun searchLectures(query: String): Flow<List<Lecture>> = dao.searchLectures(query)

    suspend fun getLectureById(id: Long): Lecture? = dao.getLectureById(id)

    suspend fun insert(lecture: Lecture): Long = dao.insert(lecture)

    suspend fun update(lecture: Lecture) = dao.update(lecture)

    suspend fun delete(lecture: Lecture) = dao.delete(lecture)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
