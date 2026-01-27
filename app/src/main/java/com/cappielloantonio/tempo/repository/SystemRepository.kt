package com.cappielloantonio.tempo.repository;

import android.util.Log
import androidx.lifecycle.MutableLiveData
import cn.sffzh.tempus.util.SubsonicManager
import com.cappielloantonio.tempo.App.Companion.getGithubClientInstance
import com.cappielloantonio.tempo.github.models.LatestRelease
import com.cappielloantonio.tempo.interfaces.SystemCallback
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension
import com.cappielloantonio.tempo.subsonic.models.ResponseStatus
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse
import com.cappielloantonio.tempo.util.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object SystemRepository {

    private const val TAG = "SystemRepository"

    @OptIn(DelicateCoroutinesApi::class)
    fun checkUserCredentialJava(callback: SystemCallback) {
        GlobalScope.launch(Dispatchers.IO) {
            checkUserCredential(callback)
        }
    }
    suspend fun checkUserCredential(callback: SystemCallback) {
        SubsonicManager.getSubsonic()
            .getSystemClient()
            .ping()
            .enqueue(object : Callback<ApiResponse?> {
                override fun onResponse(
                    call: Call<ApiResponse?>,
                    response: Response<ApiResponse?>
                ) {
                    if (response.body() != null) {
                        val resp = response.body()?.subsonicResponse
                        when (resp?.status) {
                            ResponseStatus.FAILED -> {
                                callback.onError(Exception("${resp.error!!.code.toString()} - ${resp.error?.message?:""}"))
                            }
                            ResponseStatus.OK -> {
                                val password = response.raw().request.url.queryParameter("p")
                                val token = response.raw().request.url.queryParameter("t")
                                val salt = response.raw().request.url.queryParameter("s")

                                //校验用户密码时同时更新isOpenseSubsonic字段。
                                Preferences.setOpenSubsonic(resp.isOpenseSubsonic())

                                callback.onSuccess(password, token, salt)
                            }
                            else -> {
                                callback.onError(Exception("Empty response"))
                            }
                        }
                    } else {
                        callback.onError(Exception(response.code().toString()))
                    }
                }

                override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                    callback.onError(Exception(t.message))
                }
            })
    }

    fun ping(): MutableLiveData<SubsonicResponse?> {
        val pingResult = MutableLiveData<SubsonicResponse?>()

        CoroutineScope(Dispatchers.IO).launch {
            SubsonicManager.getSubsonic()
                .getSystemClient()
                .ping()
                .enqueue(object : Callback<ApiResponse?> {
                    override fun onResponse(
                        call: Call<ApiResponse?>,
                        response: Response<ApiResponse?>
                    ) {
                        val body = response.body()
                        if (response.isSuccessful &&  body != null) {
                            pingResult.postValue(body.subsonicResponse)
                        } else {
                            pingResult.postValue(null)
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                        pingResult.postValue(null)
                    }
                })
        }

        return pingResult
    }

    fun updateOpenSubsonicExtensions(){
        CoroutineScope(Dispatchers.IO).launch {
            SubsonicManager.getSubsonic()
                .getSystemClient()
                .getOpenSubsonicExtensions()
                .enqueue(object : Callback<ApiResponse?> {
                    override fun onResponse(
                        call: Call<ApiResponse?>,
                        response: Response<ApiResponse?>
                    ) {

                        if (response.isSuccessful && response.body() != null) {
                            try{
                                val ext = response.body()?.subsonicResponse?.openSubsonicExtensions?:return
                                Preferences.setOpenSubsonicExtensions(ext)
                            }finally {
                                Preferences.markOpenSubsonicExtensionsInitialized(true)
                            }
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                    }
                })
        }
    }

    fun getOpenSubsonicExtensions(): MutableLiveData<List<OpenSubsonicExtension>?> {
        val extensionsResult = MutableLiveData<List<OpenSubsonicExtension>?>()

        Log.d(TAG, "getOpenSubsonicExtensions")

        CoroutineScope(Dispatchers.IO).launch {
            SubsonicManager.getSubsonic()
                .getSystemClient()
                .getOpenSubsonicExtensions()
                .enqueue(object : Callback<ApiResponse?> {
                    override fun onResponse(
                        call: Call<ApiResponse?>,
                        response: Response<ApiResponse?>
                    ) {
                        Log.d(TAG, "getOpenSubsonicExtensions: \n" +
                                "response.isSuccessful = ${response.isSuccessful} \n" +
                                "response.body() = ${response.body()} ")
                        if (response.isSuccessful && response.body() != null) {
                            Preferences.markOpenSubsonicExtensionsInitialized(true)
                            val ext = response.body()?.subsonicResponse?.openSubsonicExtensions?:return
                            Preferences.setOpenSubsonicExtensions(ext)
                            extensionsResult.postValue(ext)
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                        extensionsResult.postValue(null)
                    }
                })
        }

        return extensionsResult
    }

    fun checkTempoUpdate(): MutableLiveData<LatestRelease?> {
        val latestRelease = MutableLiveData<LatestRelease?>()

        getGithubClientInstance()
            .getReleaseClient()
            .getLatestRelease()
            .enqueue(object : Callback<LatestRelease?> {
                override fun onResponse(
                    call: Call<LatestRelease?>,
                    response: Response<LatestRelease?>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        latestRelease.postValue(response.body())
                    }
                }

                override fun onFailure(call: Call<LatestRelease?>, t: Throwable) {
                    latestRelease.postValue(null)
                }
            })

        return latestRelease
    }

}
