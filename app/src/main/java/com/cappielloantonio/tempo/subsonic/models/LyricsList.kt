package com.cappielloantonio.tempo.subsonic.models

import androidx.annotation.Keep

@Keep
class LyricsList {
    var structuredLyrics: List<StructuredLyrics>? = null

    companion object {
        @JvmStatic
        fun hasStructuredLyrics(list: LyricsList?): Boolean {
            return list?.structuredLyrics?.firstOrNull()?.line?.isNotEmpty() == true
        }
    }

}