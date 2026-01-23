package com.cappielloantonio.tempo.subsonic

@Deprecated(
    message = "RetrofitClient is deprecated. Use RetrofitManager instead.",
    replaceWith = ReplaceWith("RetrofitManager.createService(url, serviceClass)")
)
class RetrofitClient(subsonic: Subsonic) {
    val retrofit: Proxy = Proxy(subsonic.url)
}

class Proxy(val url: String){
    fun <T> create(serviceClass: Class<T>): T{
        return RetrofitManager.createService(url, serviceClass)
    }
}