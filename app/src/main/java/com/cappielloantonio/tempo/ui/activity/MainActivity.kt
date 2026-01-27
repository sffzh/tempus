package com.cappielloantonio.tempo.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import cn.sffzh.tempus.ui.activity.controller.AssetLinkHandler
import cn.sffzh.tempus.ui.activity.controller.BottomSheetController
import cn.sffzh.tempus.ui.activity.controller.LoginNavigator
import cn.sffzh.tempus.ui.activity.controller.MediaServiceConnector
import cn.sffzh.tempus.ui.activity.controller.NavigationController
import com.cappielloantonio.tempo.databinding.ActivityMainBinding
import com.cappielloantonio.tempo.ui.activity.base.BaseActivity
import com.cappielloantonio.tempo.util.AssetLinkUtil
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.viewmodel.LoginViewModel
import com.cappielloantonio.tempo.viewmodel.MainViewModel

@OptIn(UnstableApi::class)
class MainActivity : BaseActivity() {
    companion object{
        private const val TAG = "MainActivity"
    }

    lateinit var bind: ActivityMainBinding

    private val mainViewModel: MainViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    private lateinit var bottomSheetController: BottomSheetController
    private lateinit var navigationController: NavigationController
    private lateinit var mediaServiceConnector: MediaServiceConnector
    private lateinit var assetLinkHandler: AssetLinkHandler
    private lateinit var loginNavigator: LoginNavigator

    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        bottomSheetController = BottomSheetController(this, bind, mainViewModel)
        navigationController = NavigationController(this, bind, bottomSheetController)
        navController = navigationController.navController
        mediaServiceConnector = MediaServiceConnector(this, mainViewModel, bottomSheetController)
        assetLinkHandler = AssetLinkHandler(this, bottomSheetController)
        loginNavigator = LoginNavigator(
            this,
            navigationController,
            bottomSheetController,
            assetLinkHandler,
            loginViewModel
        )

        loginNavigator.handleInitialLoginState()
        assetLinkHandler.handleIntent(intent)

        observeOpenSubsonicExtensions()

        onBackPressedDispatcher.addCallback(this) {
            if (bottomSheetController.behavior.state ==
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            ) {
                bottomSheetController.collapseDelayed()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mediaServiceConnector.onStart()
    }



    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        assetLinkHandler.handleIntent(intent)
    }

    // 这些方法现在可以只做简单转发，或者逐步内联到控制器里
    fun resetMusicSession() = mediaServiceConnector.resetMusicSession()
    fun resetView() = navigationController.resetCurrentDestination()
    fun quit() {
        finishAndRemoveTask()
    }

    fun openAssetLink(assetLink: AssetLinkUtil.AssetLink, collapse: Boolean) = assetLinkHandler.openAssetLink(assetLink, collapse)
    fun openAssetLink(assetLink: AssetLinkUtil.AssetLink) = assetLinkHandler.openAssetLink(assetLink, true)

    fun setBottomSheetInPeek(isVisible:Boolean) = bottomSheetController.setInPeek(isVisible)

    fun setBottomNavigationBarVisibility(visible: Boolean) = navigationController.setBottomNavigationBarVisibility(visible)
    fun setBottomSheetVisibility(visible: Boolean) = bottomSheetController.setVisibility(visible)
    fun goFromLogin() = loginNavigator.goFromLogin()
    fun expandBottomSheet() = bottomSheetController.expand()
    fun setBottomSheetDraggableState(draggable: Boolean) = bottomSheetController.setDraggable(draggable)
    fun collapseBottomSheetDelayed() = bottomSheetController.collapseDelayed()

    private fun observeOpenSubsonicExtensions() {
    if (Preferences.isLogged()) {
        mainViewModel.openSubsonicExtensions.observe(this) { openSubsonicExtensions ->
            if (openSubsonicExtensions != null) {
                Preferences.setOpenSubsonicExtensions(openSubsonicExtensions)
            }
        }
    }
}
}

