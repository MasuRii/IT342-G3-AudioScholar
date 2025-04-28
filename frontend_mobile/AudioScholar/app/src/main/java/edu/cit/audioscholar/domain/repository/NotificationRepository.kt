package edu.cit.audioscholar.domain.repository

import edu.cit.audioscholar.util.Resource

interface NotificationRepository {

    suspend fun registerFcmToken(token: String): Resource<Unit>

} 