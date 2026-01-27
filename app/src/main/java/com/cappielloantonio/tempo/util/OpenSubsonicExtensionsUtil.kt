package com.cappielloantonio.tempo.util;

import android.util.Log
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch


object OpenSubsonicExtensionsUtil {
    private final const val TAG = "OpenSubsonicExtensionsUtil"

    private const val EXTENSION_NAME_TRANSCODE_OFFSET = "transcodeOffset"
    private const val EXTENSION_NAME_FORM_POST = "formPost"
    private const val EXTENSION_NAME_SONG_LYRICS = "songLyrics"

    private fun getOpenSubsonicExtensions(): MutableList<OpenSubsonicExtension?>? {
        var extensions: MutableList<OpenSubsonicExtension?>? = null

        if (Preferences.isOpenSubsonic() && Preferences.getOpenSubsonicExtensions() != null) {
            extensions = Gson().fromJson<MutableList<OpenSubsonicExtension?>?>(
                Preferences.getOpenSubsonicExtensions(),
                object : TypeToken<MutableList<OpenSubsonicExtension?>?>() {
                }.type
            )
        }

        return extensions
    }

    private fun getOpenSubsonicExtension(extensionName: String?): OpenSubsonicExtension? {
        val osExt = getOpenSubsonicExtensions()?: return null

        return osExt.stream()
            .filter { openSubsonicExtension -> openSubsonicExtension?.name == extensionName }
            .findAny().orElse(null)
    }

    fun isTranscodeOffsetExtensionAvailable(): Boolean {
        return getOpenSubsonicExtension(EXTENSION_NAME_TRANSCODE_OFFSET) != null
    }

    fun isFormPostExtensionAvailable(): Boolean {
        return getOpenSubsonicExtension(EXTENSION_NAME_FORM_POST) != null
    }

    fun isSongLyricsExtensionAvailable(): Boolean {
        return getOpenSubsonicExtension(EXTENSION_NAME_SONG_LYRICS) != null
    }

    fun isOpenSubsonicExtensionsInitialized(): Boolean {
        return Preferences.isOpenSubsonicExtensionsInitialized()
    }

    fun waitOpenSubsonicExtensionsInit(callback: Runnable) {
        if (Preferences.isOpenSubsonicExtensionsInitialized()){
            callback.run()
        }else{
            CoroutineScope(Dispatchers.IO).launch {
                Preferences.openSubsonicExtensionsInitializedFlow.filter { it }.collect {
                    Log.d(TAG, "waitOpenSubsonicExtensionsInit now start to callback")
                    callback.run()
                }
            }
        }
    }

}
