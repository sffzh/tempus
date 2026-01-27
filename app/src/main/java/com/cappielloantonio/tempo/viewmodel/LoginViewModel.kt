package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi

import com.cappielloantonio.tempo.model.Server;
import com.cappielloantonio.tempo.repository.ServerRepository;
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.util.SecurePrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import java.util.List;

@UnstableApi
class LoginViewModel : ViewModel() {
    private val serverRepository:ServerRepository = ServerRepository();

    private var toEdit:Server? = null;

    val isLoggedIn: StateFlow<Boolean> =
        Preferences.isLoggedInFlow
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                false
            )

    fun logout() {
        viewModelScope.launch {
            //迁移自MainActivity.resetUserSession()
            SecurePrefs.setPassword(null)
            SecurePrefs.setToken(null)
            SecurePrefs.setSalt(null)
            SecurePrefs.setNavidromeToken(null)

            Preferences.setServerId(null);
            Preferences.setServer(null);
            Preferences.setLocalAddress(null);
            Preferences.setUser(null);

            // TODO Enter all settings to be reset
            Preferences.setOpenSubsonic(false);
            Preferences.resetPlaybackSpeed();
            Preferences.setSkipSilenceMode(false);
            Preferences.setDataSavingMode(false);
            Preferences.setStarredSyncEnabled(false);
            Preferences.setStarredAlbumsSyncEnabled(false);
        }
    }

    val serverList: LiveData<kotlin.collections.List<Server?>?>? = serverRepository.liveServer;

    fun addServer(server: Server) {
        serverRepository.insert(server);
    }

    fun deleteServer(server: Server?) {
        if (server != null) {
            serverRepository.delete(server);
        } else if (toEdit != null) {
            serverRepository.delete(toEdit);
        }
    }

    fun setServerToEdit(server: Server?) {
        toEdit = server;
    }

    fun getServerToEdit(): Server? {
        return toEdit;
    }
}
