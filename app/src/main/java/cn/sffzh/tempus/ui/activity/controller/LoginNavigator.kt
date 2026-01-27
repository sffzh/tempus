package cn.sffzh.tempus.ui.activity.controller

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.repository.SystemRepository
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.viewmodel.LoginViewModel

class LoginNavigator @OptIn(UnstableApi::class) constructor
    (
    private val activity: MainActivity,
    private val navigationController: NavigationController,
    private val bottomSheetController: BottomSheetController,
    private val assetLinkHandler: AssetLinkHandler,
    private val loginViewModel: LoginViewModel
) {
    companion object{
        private const val TAG = "LoginNavigator"
    }

    fun handleInitialLoginState() {
        if (Preferences.isLogged()) {
            goFromLogin()
        } else {
            navigationController.goToLogin()
        }
    }

    @OptIn(UnstableApi::class)
    fun goFromLogin() {
        bottomSheetController.setInPeek(true)
        navigationController.goToHome()
        assetLinkHandler.consumePendingAssetLink()
        SystemRepository.updateOpenSubsonicExtensions()
        Log.d(TAG, "goFromLoginEnded")

    }

    @OptIn(UnstableApi::class)
    fun quit() {
        loginViewModel.logout()
        activity.resetMusicSession()
        navigationController.goToLogin()
        // 如果你更希望彻底重启：
         activity.finish()
         activity.startActivity(activity.intent)
    }
}
