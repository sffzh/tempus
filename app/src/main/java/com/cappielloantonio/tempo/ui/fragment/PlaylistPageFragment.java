package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;

import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentPlaylistPageBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaylistPageViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class PlaylistPageFragment extends Fragment implements ClickCallback {
    private static final String TAG = "PlaylistPageFragment";
    private FragmentPlaylistPageBinding bind;
    private MainActivity activity;
    private PlaylistPageViewModel playlistPageViewModel;
    private PlaybackViewModel playbackViewModel;
    private SongHorizontalAdapter songHorizontalAdapter;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistPageViewModel = new ViewModelProvider(requireActivity()).get(PlaylistPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        init();
        initAppBar();
        initMusicButton();
        initBackCover();
        initSongsView();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 使用新的 MenuProvider API
        FragmentActivity activity = super.requireActivity();
        activity.addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        // 新增：观察数据变化，触发菜单刷新
        playlistPageViewModel.isPinned(getViewLifecycleOwner()).observe(
                getViewLifecycleOwner(),
                isPinned -> {
                    activity.invalidateMenu(); // 这会重新触发 onCreateMenu/onPrepareMenu
                });
    }

    private final MenuProvider menuProvider = new MenuProvider() {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.playlist_page_menu, menu);

            MenuItem searchItem = menu.findItem(R.id.action_search);
            SearchView searchView = (SearchView) searchItem.getActionView();
            assert searchView != null;
            searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchView.clearFocus();
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (songHorizontalAdapter != null) {
                        songHorizontalAdapter.getFilter().filter(newText);
                    }
                    return false;
                }
            });

            searchView.setPadding(-32, 0, 0, 0);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            // 专门处理动态显示的逻辑
            initMenuOption(menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_download_playlist) {
                downloadPlaylist(); // 提取出的下载逻辑
                return true;
            } else if (itemId == R.id.action_pin_playlist) {
                playlistPageViewModel.setPinned(true);
                return true;
            } else if (itemId == R.id.action_unpin_playlist) {
                playlistPageViewModel.setPinned(false);
                return true;
            } else if (itemId == android.R.id.home) {
                activity.navController.navigateUp();
                return true;
            }
            return false;
        }
    };

    // 将复杂的下载逻辑提取出来
    private void downloadPlaylist() {
        playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (isVisible() && getActivity() != null) {
                if (Preferences.getDownloadDirectoryUri() == null) {
                    DownloadUtil.getDownloadTracker(requireContext()).download(
                            MappingUtil.mapDownloads(songs),
                            songs.stream().map(child -> {
                                Download toDownload = new Download(child);
                                toDownload.setPlaylistId(playlistPageViewModel.getPlaylist().getId());
                                toDownload.setPlaylistName(playlistPageViewModel.getPlaylist().getName());
                                return toDownload;
                            }).collect(Collectors.toList())
                    );
                } else {
                    songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(requireContext(), child));
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (songHorizontalAdapter != null) setMediaBrowserListenableFuture();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        playlistPageViewModel.setPlaylist(requireArguments().getParcelable(Constants.PLAYLIST_OBJECT));
    }

    private void initMenuOption(Menu menu) {
        // 1. 获取当前缓存的值，而不是设置观察者
        Boolean isPinned = playlistPageViewModel.isPinned(getViewLifecycleOwner()).getValue();
        if (isPinned == null) return;

        // 2. 增加空检查，防止菜单项尚未加载或已被移除
        MenuItem unpinItem = menu.findItem(R.id.action_unpin_playlist);
        MenuItem pinItem = menu.findItem(R.id.action_pin_playlist);

        if (unpinItem != null) {
            unpinItem.setVisible(isPinned);
        }
        if (pinItem != null) {
            pinItem.setVisible(!isPinned);
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.animToolbar.setTitle(playlistPageViewModel.getPlaylist().getName());

        bind.playlistNameLabel.setText(playlistPageViewModel.getPlaylist().getName());
        bind.playlistSongCountLabel.setText(getString(R.string.playlist_song_count, playlistPageViewModel.getPlaylist().getSongCount()));
        bind.playlistDurationLabel.setText(getString(R.string.playlist_duration, MusicUtil.getReadableDurationString(playlistPageViewModel.getPlaylist().getDuration(), false)));

        bind.animToolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });

        Objects.requireNonNull(bind.animToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void initMusicButton() {
        playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null) {
                bind.playlistPagePlayButton.setOnClickListener(v -> {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });

                bind.playlistPageShuffleButton.setOnClickListener(v -> {
                    Collections.shuffle(songs);
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });
            }
        });
    }

    private void initBackCover() {
        playlistPageViewModel.getPlaylistSongLiveList().observe(requireActivity(), songs -> {
            if (bind != null && songs != null && !songs.isEmpty()) {
                Collections.shuffle(songs);

                // Pic top-left
                CustomGlideRequest.Builder
                        .from(requireContext(), !songs.isEmpty() ? songs.get(0).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(CustomGlideRequest.CORNER_RADIUS, 0, 0, 0))
                        .into(bind.playlistCoverImageViewTopLeft);

                // Pic top-right
                CustomGlideRequest.Builder
                        .from(requireContext(), songs.size() > 1 ? songs.get(1).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(0, CustomGlideRequest.CORNER_RADIUS, 0, 0))
                        .into(bind.playlistCoverImageViewTopRight);

                // Pic bottom-left
                CustomGlideRequest.Builder
                        .from(requireContext(), songs.size() > 2 ? songs.get(2).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(0, 0, 0, CustomGlideRequest.CORNER_RADIUS))
                        .into(bind.playlistCoverImageViewBottomLeft);

                // Pic bottom-right
                CustomGlideRequest.Builder
                        .from(requireContext(), songs.size() > 3 ? songs.get(3).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(0, 0, CustomGlideRequest.CORNER_RADIUS, 0))
                        .into(bind.playlistCoverImageViewBottomRight);
            }
        });
    }

    private void initSongsView() {
        bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songRecyclerView.setHasFixedSize(true);

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.songRecyclerView.setAdapter(songHorizontalAdapter);
        setMediaBrowserListenableFuture();
        reapplyPlayback();
        Log.d(TAG, "initSongsView");
        bind.getRoot().findViewById(R.id.playlist_page_button_layout).setVisibility(View.GONE);
//        playlist_page_button_layout
        playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            Log.d(TAG, "getPlaylistSongLiveList finished");
            songHorizontalAdapter.setItems(songs);
            bind.getRoot().findViewById(R.id.playlist_page_button_layout).setVisibility(View.VISIBLE);
            reapplyPlayback();
        });
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    private void observePlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (songHorizontalAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (songHorizontalAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (songHorizontalAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            songHorizontalAdapter.setPlaybackState(id, playbackViewModel.isPlaying());
        }
    }

    private void setMediaBrowserListenableFuture() {
        songHorizontalAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }
}
