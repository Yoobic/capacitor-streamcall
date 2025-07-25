package ee.forgr.capacitor.streamcall

import android.content.Context
import android.content.SharedPreferences
import android.util.ArrayMap
import io.getstream.video.android.model.User
import org.json.JSONObject
import androidx.core.content.edit

data class UserCredentials(
    val user: User,
    val tokenValue: String
) {
    val id: String
        get() = user.id
}

interface UserRepository {
    fun save(user: UserCredentials)
    fun loadCurrentUser(): UserCredentials?
    fun removeCurrentUser()
    fun save(token: String)
}

/**
 * Note: This is just for simplicity. In a production environment,
 * consider using more secure storage solutions like EncryptedSharedPreferences
 * or a proper database with encryption.
 */
class SecureUserRepository private constructor(context: Context) : UserRepository {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "stream_video_prefs"
        private const val KEY_USER = "stream.video.user"
        private const val KEY_TOKEN = "stream.video.token"

        @Volatile
        private var instance: SecureUserRepository? = null

        fun getInstance(context: Context): SecureUserRepository {
            return instance ?: synchronized(this) {
                instance ?: SecureUserRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun save(user: UserCredentials) {
        android.util.Log.d("SecureUserRepository", "Saving user credentials for: ${user.user.id}")
        
        val customJson = user.user.custom?.let { customMap ->
            JSONObject().apply {
                customMap.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }

        val userJson = JSONObject().apply {
            put("id", user.user.id)
            put("name", user.user.name)
            put("image", user.user.image)
            put("role", user.user.role)
            put("custom", customJson)
        }

        sharedPreferences.edit {
            putString(KEY_USER, userJson.toString())
            putString(KEY_TOKEN, user.tokenValue)
        }
        
        android.util.Log.d("SecureUserRepository", "User credentials saved successfully for: ${user.user.id}")
    }

    override fun save(token: String) {
        sharedPreferences.edit { putString(KEY_TOKEN, token) }
    }

    override fun loadCurrentUser(): UserCredentials? {
        android.util.Log.d("SecureUserRepository", "Loading current user credentials")
        
        val userJson = sharedPreferences.getString(KEY_USER, null)
        val token = sharedPreferences.getString(KEY_TOKEN, null)

        return try {
            if (userJson != null && token != null) {
                val jsonObject = JSONObject(userJson)

                val user = User(
                    id = jsonObject.getString("id"),
                    name = jsonObject.optString("name"),
                    image = jsonObject.optString("image"),
                    role = jsonObject.optString("role"),
                    custom = ArrayMap()
                )
                val credentials = UserCredentials(user, token)
                android.util.Log.d("SecureUserRepository", "Successfully loaded credentials for user: ${user.id}")
                credentials
            } else {
                android.util.Log.d("SecureUserRepository", "No stored credentials found (userJson: ${userJson != null}, token: ${token != null})")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureUserRepository", "Error loading user credentials", e)
            e.printStackTrace()
            null
        }
    }

    override fun removeCurrentUser() {
        android.util.Log.d("SecureUserRepository", "Removing current user credentials")
        sharedPreferences.edit {
            remove(KEY_USER)
            remove(KEY_TOKEN)
        }
        android.util.Log.d("SecureUserRepository", "User credentials removed successfully")
    }
} 
