package com.cappielloantonio.tempo

import android.app.Application
import android.content.Context
import android.util.Log
import cn.sffzh.tempus.util.SubsonicManager
import com.cappielloantonio.tempo.github.Github
import com.cappielloantonio.tempo.helper.ThemeHelper
import com.cappielloantonio.tempo.subsonic.Subsonic
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.util.Preferences.getTheme
import com.google.crypto.tink.config.TinkConfig
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException

class App : Application() {
    companion object {
        const val TAG: String = "APP"
        lateinit var instance: App
            private set

        @JvmStatic
        fun getContext(): Context {
            return instance
        }

        @Deprecated(
            "Use suspend fun SubsonicManager.getSubsonic() instead.",
            ReplaceWith("SubsonicManager.getSubsonic()")
        )
        @JvmStatic
        fun getSubsonicClientInstance(override: Boolean): Subsonic {
            return SubsonicManager.getSubsonicBlocking()
        }

        @Deprecated(
            "Use suspend fun Preferences.resetTokenSalt() instead.",
            ReplaceWith("Preferences.resetTokenSalt()")
        )
        fun refreshSubsonicClient(){
            Preferences.resetTokenSalt()
        }

        @JvmStatic
        fun getGithubClientInstance(): Github {
            return instance.github
        }

    }
    val github: Github by lazy { Github() }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        // 1. 关键：指向系统创建的实例
        instance = this

        try {
            TinkConfig.register()
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Failed to register Tink", e)
            throw RuntimeException(e)
        }

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ThemeHelper.applyTheme(getTheme(ThemeHelper.DEFAULT_MODE))
        }

    }

}

