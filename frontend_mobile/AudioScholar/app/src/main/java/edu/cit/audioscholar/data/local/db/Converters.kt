package edu.cit.audioscholar.data.local.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto

class Converters {
    private val gson = Gson()


    @TypeConverter
    fun fromGlossaryItemList(value: List<GlossaryItemDto>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toGlossaryItemList(value: String?): List<GlossaryItemDto>? {
        return value?.let {
            val listType = object : TypeToken<List<GlossaryItemDto>>() {}.type
            gson.fromJson(it, listType)
        }
    }

    @TypeConverter
    fun fromRecommendationList(value: List<RecommendationDto>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toRecommendationList(value: String?): List<RecommendationDto>? {
        return value?.let {
            val listType = object : TypeToken<List<RecommendationDto>>() {}.type
            gson.fromJson(it, listType)
        }
    }
}
