package com.moviesrecommender.data.local

class LocalStorageService(private val dao: StarDao) {
    suspend fun addStar(tmdbId: Int) = dao.insert(StarEntity(tmdbId))
    suspend fun removeStar(tmdbId: Int) = dao.delete(StarEntity(tmdbId))
    suspend fun getStars(): List<Int> = dao.getAll()
}
