package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.MutableLiveData
import cn.sffzh.tempus.util.SubsonicManager
import com.cappielloantonio.tempo.App.Companion.getGithubClientInstance
import com.cappielloantonio.tempo.App.Companion.getSubsonicClientInstance
import com.cappielloantonio.tempo.github.models.LatestRelease
import com.cappielloantonio.tempo.interfaces.SystemCallback
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension
import com.cappielloantonio.tempo.subsonic.models.ResponseStatus
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SystemRepository {

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
                        if (response.body()!!.subsonicResponse.status == ResponseStatus.FAILED) {
                            callback.onError(Exception(response.body()!!.subsonicResponse.error!!.code.toString() + " - " + response.body()!!.subsonicResponse.error!!.message))
                        } else if (response.body()!!.subsonicResponse.status == ResponseStatus.OK) {
                            val password = response.raw().request.url.queryParameter("p")
                            val token = response.raw().request.url.queryParameter("t")
                            val salt = response.raw().request.url.queryParameter("s")
                            callback.onSuccess(password, token, salt)
                        } else {
                            callback.onError(Exception("Empty response"))
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

        getSubsonicClientInstance(false)
            .getSystemClient()
            .ping()
            .enqueue(object : Callback<ApiResponse?> {
                override fun onResponse(
                    call: Call<ApiResponse?>,
                    response: Response<ApiResponse?>
                ) {
                    if (response.isSuccessful() && response.body() != null) {
                        pingResult.postValue(response.body()!!.subsonicResponse)
                    } else {
                        pingResult.postValue(null)
                    }
                }

                override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                    pingResult.postValue(null)
                }
            })

        return pingResult
    }

    fun getOpenSubsonicExtensions(): MutableLiveData<List<OpenSubsonicExtension>?> {
        val extensionsResult = MutableLiveData<List<OpenSubsonicExtension>?>()

        getSubsonicClientInstance(false)
            .getSystemClient()
            .getOpenSubsonicExtensions()
            .enqueue(object : Callback<ApiResponse?> {
                override fun onResponse(
                    call: Call<ApiResponse?>,
                    response: Response<ApiResponse?>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        response.body()?.subsonicResponse?.openSubsonicExtensions?: return
                        extensionsResult.postValue(response.body()?.subsonicResponse?.openSubsonicExtensions)
                    }
                }

                override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                    extensionsResult.postValue(null)
                }
            })

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
                    if (response.isSuccessful() && response.body() != null) {
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
