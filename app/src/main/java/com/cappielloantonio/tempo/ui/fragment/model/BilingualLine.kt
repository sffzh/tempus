package com.cappielloantonio.tempo.ui.fragment.model

import com.cappielloantonio.tempo.subsonic.models.LyricsList

data class BilingualLine(
    val timeMs: Long,
    val primary: String,
    val secondary: String? = null
)

fun LyricsList.toBilingualLines(): List<BilingualLine> {
    val lines = structuredLyrics?.get(0)?.line ?: return emptyList()
    if (lines.isEmpty()) return emptyList()

    val result = mutableListOf<BilingualLine>()

    var i = 0
    while (i < lines.size) {
        val current = lines[i]

        if (current.start == 0 && current.value.isBlank()){
//            跳过纯空行
            i += 1
            continue
        }

        val next = lines.getOrNull(i + 1)

        // 当前行时间戳
        val time = current.start

        // 如果下一行存在且时间戳相同 → 说明是翻译行
        if (next != null && next.start == current.start) {
            result.add(
                BilingualLine(
                    timeMs = time.toLong(),
                    primary = current.value.trim(),
                    secondary = next.value.trim()
                )
            )
            i += 2 // 跳过两行
        } else {
            // 没有翻译行
            result.add(
                BilingualLine(
                    timeMs = time.toLong(),
                    primary = current.value.trim(),
                    secondary = null
                )
            )
            i += 1
        }
    }

    return result
}
