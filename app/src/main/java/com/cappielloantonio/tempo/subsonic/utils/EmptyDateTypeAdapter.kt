package com.cappielloantonio.tempo.subsonic.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// This adapter handles Date objects, returning null if the JSON string is empty or unparsable.
class EmptyDateTypeAdapter : JsonDeserializer<Date> {

    // 仅存储模式字符串，避免线程安全问题
    private val datePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date? {
        val jsonString = json.asString.trim()

        if (jsonString.isEmpty()) {
            return null
        }

        for (pattern in datePatterns) {
            try {
                // 1. 使用 Locale.US 保证跨设备稳定性
                // 2. 显式设置 UTC 时区确保正确解析服务器时间
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return sdf.parse(jsonString)
            } catch (e: ParseException) {
                // 继续尝试下一种格式
            }
        }
        
        return null 
    }
}