package cn.sffzh.tempus.util

import android.util.Log
import com.cappielloantonio.tempo.subsonic.Subsonic
import com.cappielloantonio.tempo.subsonic.SubsonicPreferences
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.util.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


object SubsonicManager {
    private const val TAG = "SubsonicManager"

    private val mutex = Mutex()
    private var subsonic: Subsonic? = null

    private val _subsonicFlow = MutableStateFlow<Subsonic?>(null)
    val subsonicFlow: StateFlow<Subsonic?> = _subsonicFlow

    private var isLogout: Boolean = false

    // Flow 监听 token/salt 的变化
    private val tokenSaltFlow = combine(
        SecurePrefs.tokenFlow,
        SecurePrefs.saltFlow
    ) { token, salt ->
        if (token != null && salt != null) {
            //如果获取subsoic对象时未初始化，则强制使用password重新刷新Token.
            val preferences = SubsonicPreferences()
            preferences.serverUrl = Preferences.getInUseServerAddress()
            preferences.username = Preferences.getUser()
            preferences.setAuthentication(null, token, salt, false)
            preferences
        } else null
    }.filterNotNull()
        .distinctUntilChanged()




    init {
        // 当任意参数变化时，自动重建 Subsonic
        CoroutineScope(Dispatchers.IO).launch {
            SecurePrefs.passwordFlow .filterNotNull()
                .distinctUntilChanged()
                .collect { password ->
                    if (Preferences.isLowScurity()){
                        //不安全模式直接从pasoword重新创建Subsonic对象。
                        val preferences = SubsonicPreferences()
                        preferences.serverUrl = Preferences.getInUseServerAddress()
                        preferences.username = Preferences.getUser()
                        preferences.setAuthentication(password, null, null, true)
                        rebuildSubsonic(preferences)
                    }else{
                        val auth = SubsonicPreferences.SubsonicAuthentication(password, false)
                        SecurePrefs.setToken(auth.token, auth.salt)
                    }

            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            tokenSaltFlow.collect { preferences ->
                rebuildSubsonic(preferences)
            }
        }
    }

    suspend fun getSubsonic(): Subsonic {
        subsonic?.let { return it }
        if (isLogout) {
            Log.e(TAG, "getSubsonic 尝试在未登录状态下获取Subsonic对象")
            throw RuntimeException("User has been logged out.")
        }
        return subsonicFlow.filterNotNull().first()
    }

    private suspend fun rebuildSubsonic(prefs: SubsonicPreferences) {
        mutex.withLock {
            val client = Subsonic(prefs)
            subsonic = client
            _subsonicFlow.value = client
        }
    }

    @Deprecated(
        message = "Use suspend fun getSubsonic() instead",
        replaceWith = ReplaceWith("getSubsonic()")
    )
    fun getSubsonicBlocking(): Subsonic =
        runBlocking { getSubsonic() }

    @JvmStatic
    fun clearLogin() {
        isLogout = true
        subsonic = null
        _subsonicFlow.value = null
    }
}