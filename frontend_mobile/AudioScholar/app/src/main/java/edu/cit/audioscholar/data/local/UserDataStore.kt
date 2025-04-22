package edu.cit.audioscholar.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

@Singleton
class UserDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val USER_PROFILE_IMAGE_URL = stringPreferencesKey("user_profile_image_url")
        val USER_FIRST_NAME = stringPreferencesKey("user_first_name")
        val USER_LAST_NAME = stringPreferencesKey("user_last_name")
    }

    val userProfileFlow: Flow<UserProfileDto?> = context.userDataStore.data
        .map { preferences ->
            val userId = preferences[PreferencesKeys.USER_ID]
            val email = preferences[PreferencesKeys.USER_EMAIL]
            val displayName = preferences[PreferencesKeys.USER_DISPLAY_NAME]
            val profileImageUrl = preferences[PreferencesKeys.USER_PROFILE_IMAGE_URL]
            val firstName = preferences[PreferencesKeys.USER_FIRST_NAME]
            val lastName = preferences[PreferencesKeys.USER_LAST_NAME]

            if (email != null || userId != null) {
                UserProfileDto(
                    userId = userId,
                    email = email,
                    displayName = displayName,
                    profileImageUrl = profileImageUrl,
                    firstName = firstName,
                    lastName = lastName
                )
            } else {
                null
            }
        }

    suspend fun saveUserProfile(profile: UserProfileDto) {
        context.userDataStore.edit { preferences ->
            profile.userId?.let { preferences[PreferencesKeys.USER_ID] = it } ?: preferences.remove(PreferencesKeys.USER_ID)
            profile.email?.let { preferences[PreferencesKeys.USER_EMAIL] = it } ?: preferences.remove(PreferencesKeys.USER_EMAIL)
            profile.displayName?.let { preferences[PreferencesKeys.USER_DISPLAY_NAME] = it } ?: preferences.remove(PreferencesKeys.USER_DISPLAY_NAME)
            profile.profileImageUrl?.let { preferences[PreferencesKeys.USER_PROFILE_IMAGE_URL] = it } ?: preferences.remove(PreferencesKeys.USER_PROFILE_IMAGE_URL)
            profile.firstName?.let { preferences[PreferencesKeys.USER_FIRST_NAME] = it } ?: preferences.remove(PreferencesKeys.USER_FIRST_NAME)
            profile.lastName?.let { preferences[PreferencesKeys.USER_LAST_NAME] = it } ?: preferences.remove(PreferencesKeys.USER_LAST_NAME)
        }
    }

    suspend fun clearUserProfile() {
        context.userDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_ID)
            preferences.remove(PreferencesKeys.USER_EMAIL)
            preferences.remove(PreferencesKeys.USER_DISPLAY_NAME)
            preferences.remove(PreferencesKeys.USER_PROFILE_IMAGE_URL)
            preferences.remove(PreferencesKeys.USER_FIRST_NAME)
            preferences.remove(PreferencesKeys.USER_LAST_NAME)
        }
    }
}