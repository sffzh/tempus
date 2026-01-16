package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerLyricsBinding
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.subsonic.models.LyricsList
import com.cappielloantonio.tempo.ui.fragment.model.toBilingualLines
import com.cappielloantonio.tempo.util.MusicUtil
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@UnstableApi
class PlayerLyricsFragment : Fragment() {

    private var bind: InnerFragmentPlayerLyricsBinding? = null
    private lateinit var viewModel: PlayerBottomSheetViewModel

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var mediaBrowser: MediaBrowser? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val browser = mediaBrowser
            val b = bind
            if (browser != null && b != null && browser.isConnected) {
                // 只有在播放时才频繁刷新，节省性能
                if (browser.isPlaying) {
                    b.lyricView.setProgress(browser.currentPosition)
                }
            }
            handler.postDelayed(this, 50)
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bind = InnerFragmentPlayerLyricsBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[PlayerBottomSheetViewModel::class.java]
        return bind!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initButtons()
        observeLyrics()
        observeDownloadState()
    }

    override fun onStart() {
        super.onStart()
        browserFuture = MediaBrowser.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), MediaService::class.java))
        ).buildAsync()
    }

    override fun onResume() {
        super.onResume()
        bindMediaBrowser()
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(progressRunnable)
        if (!Preferences.isDisplayAlwaysOn()) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onStop() {
        browserFuture?.cancel(true)
        mediaBrowser?.release()
        mediaBrowser = null
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bind = null
    }

    private fun initButtons() {
        bind?.syncLyricsTapButton?.setOnClickListener {
            viewModel.changeSyncLyricsState()
        }

        bind?.downloadLyricsButton?.setOnClickListener {
            val saved = viewModel.downloadCurrentLyrics()
            Toast.makeText(
                requireContext(),
                if (saved) R.string.player_lyrics_download_success else R.string.player_lyrics_download_failure,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 在 bindMediaBrowser 成功后
    private fun bindMediaBrowser() {
        browserFuture?.addListener({
            try {
                mediaBrowser = browserFuture?.get()
                handler.removeCallbacks(progressRunnable) // 先移除防止重复
                handler.post(progressRunnable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun observeLyrics() {
        viewModel.liveLyricsList.observe(viewLifecycleOwner) { lyricsList ->
            if (hasStructuredLyrics(lyricsList)) {
                val lyricLines = lyricsList.toBilingualLines()
                bind?.lyricView?.setLyrics(lyricLines)

                bind?.lyricView?.setOnLineClickListener { timeMs, _ ->
                    mediaBrowser?.seekTo(timeMs)
                }

                bind?.lyricView?.visibility = View.VISIBLE
                bind?.nowPlayingSongLyricsSrollView?.visibility = View.GONE
                bind?.nowPlayingSongLyricsTextView?.visibility = View.GONE
                bind?.emptyDescriptionImageView?.visibility = View.GONE
                bind?.titleEmptyDescriptionLabel?.visibility = View.GONE
//                目前没有拖拽歌词的功能，先把这个按钮直接隐藏掉
                bind?.syncLyricsTapButton?.visibility = View.GONE
                bind?.downloadLyricsButton?.visibility = View.VISIBLE
                bind?.downloadLyricsButton?.isEnabled = true

            } else {
                // fallback: plain text lyrics
                val text = viewModel.liveLyrics.value ?: viewModel.liveDescription.value
                if (!text.isNullOrBlank()) {
                    bind?.nowPlayingSongLyricsSrollView?.visibility = View.VISIBLE
                    bind?.nowPlayingSongLyricsTextView?.text = MusicUtil.getReadableLyrics(text)
                    bind?.nowPlayingSongLyricsTextView?.visibility = View.VISIBLE
                    bind?.lyricView?.visibility = View.GONE
                    bind?.emptyDescriptionImageView?.visibility = View.GONE
                    bind?.titleEmptyDescriptionLabel?.visibility = View.GONE
                    bind?.syncLyricsTapButton?.visibility = View.GONE
                    bind?.downloadLyricsButton?.visibility = View.VISIBLE
                } else {
                    bind?.lyricView?.visibility = View.GONE
                    bind?.nowPlayingSongLyricsSrollView?.visibility = View.GONE
                    bind?.nowPlayingSongLyricsTextView?.visibility = View.GONE
                    bind?.emptyDescriptionImageView?.visibility = View.VISIBLE
                    bind?.titleEmptyDescriptionLabel?.visibility = View.VISIBLE
                    bind?.syncLyricsTapButton?.visibility = View.GONE
                    bind?.downloadLyricsButton?.visibility = View.GONE
                }
            }
        }
    }

    private fun observeDownloadState() {
        viewModel.lyricsCachedState.observe(viewLifecycleOwner) { cached ->
            val btn = bind?.downloadLyricsButton ?: return@observe
            if (cached == true) {
                btn.setIconResource(R.drawable.ic_done)
                btn.contentDescription = getString(R.string.player_lyrics_downloaded_content_description)
            } else {
                btn.setIconResource(R.drawable.ic_download)
                btn.contentDescription = getString(R.string.player_lyrics_download_content_description)
            }
        }
    }

    private fun hasStructuredLyrics(list: LyricsList?): Boolean {
        return list?.structuredLyrics?.firstOrNull()?.line?.isNotEmpty() == true
    }

}
