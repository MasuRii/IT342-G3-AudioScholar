package edu.cit.audioscholar.util

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.ui.auth.LoginViewModel
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumStatusManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val TAG = "PremiumStatusManager"
        private const val KEY_IS_PREMIUM = "is_premium_user"
        private const val ROLE_PREMIUM = "ROLE_PREMIUM"
    }

    fun updatePremiumStatus(userProfile: UserProfileDto?) {
        var isPremium = userProfile?.roles?.contains(ROLE_PREMIUM) ?: false
        
        Log.d(TAG, "Updating premium status. User has roles: ${userProfile?.roles}, isPremium: $isPremium")
        
        if (!isPremium && userProfile?.roles.isNullOrEmpty()) {
            isPremium = isPremiumFromJwtToken()
            Log.d(TAG, "Fallback to JWT token check: isPremium = $isPremium")
        }
        
        with(sharedPreferences.edit()) {
            putBoolean(KEY_IS_PREMIUM, isPremium)
            apply()
        }
    }
    
    fun updatePremiumStatus(isPremium: Boolean) {
        Log.d(TAG, "Setting premium status directly: $isPremium")
        
        with(sharedPreferences.edit()) {
            putBoolean(KEY_IS_PREMIUM, isPremium)
            apply()
        }
    }

    fun isPremiumUser(): Boolean {
        val fromPrefs = sharedPreferences.getBoolean(KEY_IS_PREMIUM, false)
        
        return if (!fromPrefs) {
            val fromJwt = isPremiumFromJwtToken()
            if (fromJwt) {
                updatePremiumStatus(true)
                Log.d(TAG, "Updated premium status from JWT token")
            }
            fromJwt
        } else {
            fromPrefs
        }
    }
    
    fun clearPremiumStatus() {
        Log.d(TAG, "Clearing premium status")
        
        with(sharedPreferences.edit()) {
            remove(KEY_IS_PREMIUM)
            apply()
        }
    }
    
    private fun isPremiumFromJwtToken(): Boolean {
        val token = sharedPreferences.getString(LoginViewModel.KEY_AUTH_TOKEN, null) ?: return false
        
        try {
            val parts = token.split(".")
            if (parts.size < 2) return false
            
            val payloadJson = getJson(parts[1])
            
            val roles = payloadJson.optString("roles", "")
            val isPremium = roles.contains(ROLE_PREMIUM)
            
            Log.d(TAG, "JWT token roles: $roles, isPremium: $isPremium")
            return isPremium
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JWT token", e)
            return false
        }
    }
    
    private fun getJson(encodedString: String): JSONObject {
        val decodedBytes = Base64.decode(encodedString, Base64.URL_SAFE)
        val decodedString = String(decodedBytes)
        return JSONObject(decodedString)
    }
} 