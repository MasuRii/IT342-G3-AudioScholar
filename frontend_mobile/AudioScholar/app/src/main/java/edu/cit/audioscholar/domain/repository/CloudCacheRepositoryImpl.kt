package edu.cit.audioscholar.domain.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cit.audioscholar.data.local.dao.CloudCacheDao
import edu.cit.audioscholar.data.local.model.CloudCacheEntity
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto
import edu.cit.audioscholar.data.remote.dto.SummaryResponseDto
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_CLOUD_CACHE_REPO = "CloudCacheRepo"

@Singleton
class CloudCacheRepositoryImpl @Inject constructor(
    private val cloudCacheDao: CloudCacheDao,
    private val gson: Gson
) : CloudCacheRepository {

    override suspend fun getCache(remoteId: String): CloudCacheEntity? {
        Log.d(TAG_CLOUD_CACHE_REPO, "Getting cache for remoteId: $remoteId")
        return cloudCacheDao.getByRemoteId(remoteId)
    }

    override suspend fun saveSummaryToCache(remoteId: String, summary: SummaryResponseDto, timestampMillis: Long) {
        Log.d(TAG_CLOUD_CACHE_REPO, "Saving summary to cache for remoteId: $remoteId with timestamp: $timestampMillis")
        val existingCache = cloudCacheDao.getByRemoteId(remoteId)
        val glossaryJson = try { gson.toJson(summary.glossary) } catch (e: Exception) { Log.e(TAG_CLOUD_CACHE_REPO, "Error serializing glossary", e); existingCache?.glossaryJson }

        val newCache = CloudCacheEntity(
            remoteRecordingId = remoteId,
            summaryText = summary.formattedSummaryText ?: existingCache?.summaryText,
            glossaryJson = glossaryJson,
            recommendationsJson = existingCache?.recommendationsJson,
            cacheTimestampMillis = timestampMillis
        )
        cloudCacheDao.insertOrUpdate(newCache)
        Log.i(TAG_CLOUD_CACHE_REPO, "Summary cache updated for remoteId: $remoteId")
    }

    override suspend fun saveRecommendationsToCache(remoteId: String, recommendations: List<RecommendationDto>, timestampMillis: Long) {
        Log.d(TAG_CLOUD_CACHE_REPO, "Saving recommendations to cache for remoteId: $remoteId with timestamp: $timestampMillis")
        val existingCache = cloudCacheDao.getByRemoteId(remoteId)
        val recommendationsJson = try { gson.toJson(recommendations) } catch (e: Exception) { Log.e(TAG_CLOUD_CACHE_REPO, "Error serializing recommendations", e); existingCache?.recommendationsJson }

        val newCache = CloudCacheEntity(
            remoteRecordingId = remoteId,
            summaryText = existingCache?.summaryText,
            glossaryJson = existingCache?.glossaryJson,
            recommendationsJson = recommendationsJson,
            cacheTimestampMillis = timestampMillis
        )
        cloudCacheDao.insertOrUpdate(newCache)
        Log.i(TAG_CLOUD_CACHE_REPO, "Recommendations cache updated for remoteId: $remoteId")
    }

    override suspend fun deleteCache(remoteId: String) {
        Log.d(TAG_CLOUD_CACHE_REPO, "Deleting cache for remoteId: $remoteId")
        cloudCacheDao.deleteById(remoteId)
    }

    override fun parseRecommendations(json: String?): List<RecommendationDto>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<List<RecommendationDto>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG_CLOUD_CACHE_REPO, "Failed to parse Recommendations JSON", e)
            null
        }
    }

    override fun parseGlossary(json: String?): List<GlossaryItemDto>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<List<GlossaryItemDto>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG_CLOUD_CACHE_REPO, "Failed to parse Glossary JSON", e)
            null
        }
    }
}
