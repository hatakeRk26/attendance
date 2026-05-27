import android.content.Context

// UserDataManager.kt (例)
object UserDataManager {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_IS_REGISTERED = "is_registered"
    private const val KEY_STUDENT_ID = "student_id"
    private const val KEY_USER_NAME = "user_name"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getStudentId(context: Context): String {
        return getPrefs(context).getString("student_id", "") ?: ""
    }

    fun getUserName(context: Context): String {
        return getPrefs(context).getString("user_name", "") ?: ""
    }

    // 登録済みかどうか
    fun isRegistered(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_REGISTERED, false)
    }

    // データを保存する
    fun saveUser(context: Context, studentId: String, name: String) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_IS_REGISTERED, true)
            putString(KEY_STUDENT_ID, studentId)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }
}