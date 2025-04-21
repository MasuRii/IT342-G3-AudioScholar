package edu.cit.audioscholar.domain.repository

import edu.cit.audioscholar.data.local.model.RecordingMetadata
import kotlinx.coroutines.flow.Flow

interface LocalAudioRepository {
    fun getRecordingMetadata(filePath: String): Flow<Result<RecordingMetadata>>
    fun getLocalRecordings(): Flow<List<RecordingMetadata>>
    suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean
    suspend fun deleteLocalRecordings(filePaths: List<String>): Boolean
    suspend fun updateRecordingTitle(filePath: String, newTitle: String): Boolean
}