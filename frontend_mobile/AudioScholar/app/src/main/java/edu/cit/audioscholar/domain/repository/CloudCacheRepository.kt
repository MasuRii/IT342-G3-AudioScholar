package edu.cit.audioscholar.domain.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cit.audioscholar.data.local.dao.CloudCacheDao
import edu.cit.audioscholar.data.local.model.CloudCacheEntity
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto
import edu.cit.audioscholar.data.remote.dto.SummaryResponseDto
import javax.inject.Inject
import javax.inject.Singleton

interface CloudCacheRepository {
    suspend fun getCache(remoteId: String): CloudCacheEntity?
    suspend fun saveSummaryToCache(remoteId: String, summary: SummaryResponseDto, timestampMillis: Long = System.currentTimeMillis())
    suspend fun saveRecommendationsToCache(remoteId: String, recommendations: List<RecommendationDto>, timestampMillis: Long = System.currentTimeMillis())
    suspend fun deleteCache(remoteId: String)
    fun parseRecommendations(json: String?): List<RecommendationDto>?
    fun parseGlossary(json: String?): List<GlossaryItemDto>?
}
