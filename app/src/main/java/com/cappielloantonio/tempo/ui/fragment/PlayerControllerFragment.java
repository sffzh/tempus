package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.RepeatModeUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.transition.TransitionManager;
import androidx.viewpager2.widget.ViewPager2;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerControllerBinding;
import com.cappielloantonio.tempo.service.EqualizerManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.RatingDialog;
import com.cappielloantonio.tempo.ui.dialog.TrackInfoDialog;
import com.cappielloantonio.tempo.ui.fragment.pager.PlayerControllerHorizontalPager;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.cappielloantonio.tempo.viewmodel.RatingViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.elevation.SurfaceColors;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class PlayerControllerFragment extends Fragment {
    private static final String TAG = "PlayerCoverFragment";

    private InnerFragmentPlayerControllerBinding bind;
    private ViewPager2 playerMediaCoverViewPager;
    private ToggleButton buttonFavorite;
    private RatingViewModel ratingViewModel;
    private RatingBar songRatingBar;
    private TextView playerMediaTitleLabel;
    private TextView playerArtistNameLabel;
    private Button playbackSpeedButton;
    private ToggleButton skipSilenceToggleButton;
    private Chip playerMediaExtension;
    private TextView playerMediaBitrate;
    private ConstraintLayout playerQuickActionView;
    private ImageButton playerOpenQueueButton;
    private ImageButton playerTrackInfo;
    private LinearLayout ratingContainer;
    private ImageButton equalizerButton;
    private ChipGroup assetLinkChipGroup;
    private Chip playerSongLinkChip;
    private Chip playerAlbumLinkChip;
    private Chip playerArtistLinkChip;

    private MainActivity activity;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private MediaService.LocalBinder mediaServiceBinder;
    private boolean isServiceBound = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = InnerFragmentPlayerControllerBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);
        ratingViewModel = new ViewModelProvider(requireActivity()).get(RatingViewModel.class);

        init();
        initQuickActionView();
        initCoverLyricsSlideView();
        initMediaListenable();
        initMediaLabelButton();
        initArtistLabelButton();
        initEqualizerButton();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 初始化布局状态
        if (getActivity() != null) {
            updateLayoutConstraints(getActivity().isInMultiWindowMode(),
                    getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE,
                    getResources().getConfiguration().screenHeightDp);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        // 检查是否处于分屏模式
        boolean isSplitScreen = requireActivity().isInMultiWindowMode();
        updateLayoutConstraints(isSplitScreen, isLandscape, newConfig.screenHeightDp);
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        bindMediaController();
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        playerMediaCoverViewPager = bind.getRoot().findViewById(R.id.player_media_cover_view_pager);
        buttonFavorite = bind.getRoot().findViewById(R.id.button_favorite);
        playerMediaTitleLabel = bind.getRoot().findViewById(R.id.player_media_title_label);
        playerArtistNameLabel = bind.getRoot().findViewById(R.id.player_artist_name_label);
        playbackSpeedButton = bind.getRoot().findViewById(R.id.player_playback_speed_button);
        skipSilenceToggleButton = bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button);
        playerMediaExtension = bind.getRoot().findViewById(R.id.player_media_extension);
        playerMediaBitrate = bind.getRoot().findViewById(R.id.player_media_bitrate);
        playerQuickActionView = bind.getRoot().findViewById(R.id.player_quick_action_view);
        playerOpenQueueButton = bind.getRoot().findViewById(R.id.player_open_queue_button);
        playerTrackInfo = bind.getRoot().findViewById(R.id.player_info_track);
        songRatingBar =  bind.getRoot().findViewById(R.id.song_rating_bar);
        ratingContainer = bind.getRoot().findViewById(R.id.rating_container);
        equalizerButton = bind.getRoot().findViewById(R.id.player_open_equalizer_button);
        assetLinkChipGroup = bind.getRoot().findViewById(R.id.asset_link_chip_group);
        playerSongLinkChip = bind.getRoot().findViewById(R.id.asset_link_song_chip);
        playerAlbumLinkChip = bind.getRoot().findViewById(R.id.asset_link_album_chip);
        playerArtistLinkChip = bind.getRoot().findViewById(R.id.asset_link_artist_chip);
        checkAndSetRatingContainerVisibility();
    }

    private void initQuickActionView() {
        playerQuickActionView.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8));

        playerOpenQueueButton.setOnClickListener(view -> {
            PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
            if (playerBottomSheetFragment != null) {
                playerBottomSheetFragment.goToQueuePage();
            }
        });
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private int getNavigationBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private int pixToDP(int pix){
        float density = getResources().getDisplayMetrics().density;
        return  (int) (pix / density);
    }

    private void keepItemInsideView(ConstraintSet constraintSet, int buttonId, int viewId){
        constraintSet.connect(buttonId, ConstraintSet.TOP, viewId, ConstraintSet.TOP);
        constraintSet.connect(buttonId, ConstraintSet.END, viewId, ConstraintSet.END);
        constraintSet.connect(buttonId, ConstraintSet.START, viewId, ConstraintSet.START);
        constraintSet.connect(buttonId, ConstraintSet.BOTTOM, viewId, ConstraintSet.BOTTOM);
    }

    private void updatePlayControllButtons(ConstraintSet constraintSet){
        keepItemInsideView(constraintSet, R.id.exo_play_pause,  R.id.player_play_pause_placeholder_view);
        keepItemInsideView(constraintSet, R.id.exo_prev,  R.id.placeholder_view_middle_left);
        keepItemInsideView(constraintSet, R.id.exo_next,  R.id.placeholder_view_middle_right);
        keepItemInsideView(constraintSet, R.id.exo_shuffle,  R.id.placeholder_view_left);
        keepItemInsideView(constraintSet, R.id.exo_repeat_toggle,  R.id.placeholder_view_right);
    }

    private void clearAllConstraint(ConstraintSet constraintSet, int id){
        constraintSet.clear(id, ConstraintSet.TOP);
        constraintSet.clear(id, ConstraintSet.BOTTOM);
        constraintSet.clear(id, ConstraintSet.START);
        constraintSet.clear(id, ConstraintSet.END);
        constraintSet.clear(id, ConstraintSet.LEFT);
        constraintSet.clear(id, ConstraintSet.RIGHT);
    }


    private void updateLayoutConstraints(boolean isSplit, boolean isLandScape, int currentHeightDp) {
        if (bind == null || getView() == null ) return;
        ConstraintLayout constraintLayout = bind.getRoot().findViewById(R.id.now_playing_media_controller_layout);

        if (constraintLayout == null) return;
        ConstraintSet constraintSet = new ConstraintSet();

        constraintSet.clone(constraintLayout);

        if (isSplit) {
            constraintSet.create(R.id.guideline, ConstraintSet.HORIZONTAL_GUIDELINE);
            if (currentHeightDp > 600){
                constraintSet.setGuidelinePercent(R.id.guideline, 1 - 220.0f/currentHeightDp);
            }else if (currentHeightDp > 500){
                constraintSet.setGuidelinePercent(R.id.guideline, 1 - 180.0f/currentHeightDp);
            }else{
                constraintSet.setGuidelinePercent(R.id.guideline, 1 - 150.0f/currentHeightDp);
            }

            constraintSet.setVisibility(R.id.player_asset_link_row, View.GONE);
            constraintSet.setVisibility(R.id.player_media_quality_sector, View.GONE);

            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.BOTTOM, R.id.guideline, ConstraintSet.TOP);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);

            constraintSet.setVisibility(R.id.rating_container, View.GONE);

            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.END, R.id.button_favorite, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            //强制隐藏了五星评分栏，因此标题栏的约束也要受影响。
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.TOP, R.id.player_media_cover_view_pager, ConstraintSet.BOTTOM);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.TOP, 18);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.START, 24);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.END, 24);

            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.TOP, R.id.player_media_title_label, ConstraintSet.BOTTOM);

            constraintSet.setMargin(R.id.player_artist_name_label, ConstraintSet.TOP, 8);
            constraintSet.setMargin(R.id.player_artist_name_label, ConstraintSet.START, 24);
            constraintSet.setMargin(R.id.player_artist_name_label, ConstraintSet.END, 24);

            constraintSet.clear(R.id.exo_progress, ConstraintSet.BOTTOM);
            constraintSet.setMargin(R.id.exo_progress, ConstraintSet.TOP, 8);
            constraintSet.connect(R.id.exo_progress, ConstraintSet.TOP, R.id.player_artist_name_label , ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.exo_progress, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);

            constraintSet.connect(R.id.exo_position, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.setMargin(R.id.exo_position, ConstraintSet.START, 24);

            constraintSet.setVisibility(R.id.player_quick_action_view, View.GONE);


            // 统一清理并设置垂直约束
            constraintSet.connect(R.id.player_play_pause_placeholder_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.placeholder_view_left, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);

            constraintSet.setMargin(R.id.player_play_pause_placeholder_view, ConstraintSet.TOP, 0);
            if (isLandScape){
                constraintSet.setMargin(R.id.player_play_pause_placeholder_view, ConstraintSet.BOTTOM, pixToDP(getNavigationBarHeight()));
            }

        } else if (isLandScape){

            // --- 恢复正常横屏布局 ---
            // 恢复辅助线
            constraintSet.create(R.id.guideline, ConstraintSet.VERTICAL_GUIDELINE);
            constraintSet.setGuidelinePercent(R.id.guideline, 0.59f);
            constraintSet.setVisibility(R.id.player_media_cover_view_pager, View.VISIBLE);
            constraintSet.setVisibility(R.id.player_media_quality_sector, View.VISIBLE);

            constraintSet.constrainWidth(R.id.player_media_quality_sector, 0);
            constraintSet.connect(R.id.player_media_quality_sector, ConstraintSet.START, R.id.guideline, ConstraintSet.END);
            constraintSet.connect(R.id.player_media_quality_sector, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);

            constraintSet.setVisibility(R.id.player_asset_link_row, View.GONE);

            // 重新建立其他的核心约束 (Bias 必须在有 Top 和 Bottom 约束的情况下才生效)
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.END, R.id.guideline, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            // bias 值 (float)，0.0 是顶部，1.0 是底部，0.5 是中间
            constraintSet.setVerticalBias(R.id.player_media_cover_view_pager, 0.521f);
            constraintSet.setHorizontalBias(R.id.player_media_cover_view_pager, 0.8f);

            constraintSet.connect(R.id.rating_container, ConstraintSet.START, R.id.guideline, ConstraintSet.START);
            constraintSet.connect(R.id.rating_container, ConstraintSet.TOP, R.id.player_media_quality_sector, ConstraintSet.BOTTOM);

            if (currentHeightDp > 600){
                //屏幕较大时横屏模式下将进度条置于右侧正中水平线。
                constraintSet.connect(R.id.exo_progress, ConstraintSet.START, R.id.guideline, ConstraintSet.END);
                constraintSet.connect(R.id.exo_progress, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                constraintSet.connect(R.id.exo_progress, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                constraintSet.connect(R.id.player_media_title_label, ConstraintSet.BOTTOM, R.id.player_artist_name_label, ConstraintSet.TOP);
                constraintSet.clear(R.id.player_artist_name_label, ConstraintSet.TOP);
            }

            constraintSet.connect(R.id.exo_progress, ConstraintSet.START, R.id.guideline, ConstraintSet.END);
            constraintSet.setMargin(R.id.exo_progress, ConstraintSet.TOP, 20);


            constraintSet.setVerticalChainStyle(R.id.player_media_title_label, ConstraintSet.CHAIN_PACKED);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.START, R.id.guideline, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.END, R.id.button_favorite, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.TOP, R.id.rating_container, ConstraintSet.BOTTOM);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.TOP, 0);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.START, 24);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.END, 24);

            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.START, R.id.guideline, ConstraintSet.END);
            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.TOP, R.id.player_media_title_label, ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.BOTTOM, R.id.exo_progress, ConstraintSet.TOP);

            constraintSet.connect(R.id.exo_position, ConstraintSet.START, R.id.guideline, ConstraintSet.END);

            constraintSet.connect(R.id.player_play_pause_placeholder_view, ConstraintSet.START, R.id.guideline, ConstraintSet.END);
            constraintSet.setMargin(R.id.player_play_pause_placeholder_view, ConstraintSet.TOP, 36);

            constraintSet.connect(R.id.placeholder_view_left, ConstraintSet.START, R.id.guideline, ConstraintSet.END);

            constraintSet.setVisibility(R.id.player_quick_action_view, View.GONE);

        }else { // 竖屏布局
            constraintSet.create(R.id.guideline, ConstraintSet.HORIZONTAL_GUIDELINE);
//            constraintSet.setGuidelinePercent(R.id.guideline, 0.575f);
            constraintSet.setGuidelinePercent(R.id.guideline, 1 - 210.0f/currentHeightDp);

            constraintSet.setVisibility(R.id.player_asset_link_row, View.GONE);
            constraintSet.setVisibility(R.id.player_media_quality_sector, View.VISIBLE);

            constraintSet.constrainWidth(R.id.player_media_quality_sector, 0);
            constraintSet.clear(R.id.player_media_quality_sector, ConstraintSet.START);
            constraintSet.clear(R.id.player_media_quality_sector, ConstraintSet.TOP);
            constraintSet.connect(R.id.player_media_quality_sector, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_quality_sector, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);

            constraintSet.clear(R.id.player_media_cover_view_pager, ConstraintSet.TOP);
            constraintSet.clear(R.id.player_media_cover_view_pager, ConstraintSet.BOTTOM);
            constraintSet.clear(R.id.player_media_cover_view_pager, ConstraintSet.START);
            constraintSet.clear(R.id.player_media_cover_view_pager, ConstraintSet.END);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.TOP, R.id.player_media_title_label, ConstraintSet.BOTTOM);
//            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.TOP, R.id.player_media_quality_sector, ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.BOTTOM, R.id.guideline, ConstraintSet.TOP);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            constraintSet.connect(R.id.player_media_cover_view_pager, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);

            constraintSet.clear(R.id.rating_container, ConstraintSet.START);
            constraintSet.clear(R.id.rating_container, ConstraintSet.TOP);
            constraintSet.connect(R.id.rating_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.rating_container, ConstraintSet.TOP, R.id.guideline, ConstraintSet.BOTTOM);

            constraintSet.clear(R.id.player_media_title_label, ConstraintSet.END);
            constraintSet.clear(R.id.player_media_title_label, ConstraintSet.START);
            constraintSet.clear(R.id.player_media_title_label, ConstraintSet.TOP);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.END, R.id.button_favorite, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.TOP, R.id.player_media_quality_sector, ConstraintSet.BOTTOM);
//            constraintSet.connect(R.id.player_media_title_label, ConstraintSet.TOP, R.id.rating_container, ConstraintSet.BOTTOM);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.TOP, 18);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.START, 48);
            constraintSet.setMargin(R.id.player_media_title_label, ConstraintSet.END, 24);

            constraintSet.clear(R.id.player_artist_name_label, ConstraintSet.BOTTOM);
            constraintSet.clear(R.id.player_artist_name_label, ConstraintSet.START);
            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.TOP, R.id.rating_container, ConstraintSet.BOTTOM);
//            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.TOP, R.id.player_media_title_label, ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.player_artist_name_label, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.setMargin(R.id.player_artist_name_label, ConstraintSet.TOP, 8);
            constraintSet.setMargin(R.id.player_artist_name_label, ConstraintSet.START, 24);
            constraintSet.setMargin(R.id.player_artist_name_label, ConstraintSet.END, 24);

            constraintSet.clear(R.id.exo_position, ConstraintSet.START);
            constraintSet.connect(R.id.exo_position, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.setMargin(R.id.exo_position, ConstraintSet.START, 24);

            constraintSet.clear(R.id.exo_progress, ConstraintSet.BOTTOM);
            constraintSet.setMargin(R.id.exo_progress, ConstraintSet.TOP, 8);
            constraintSet.connect(R.id.exo_progress, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.setMargin(R.id.exo_progress, ConstraintSet.START, 16);

            constraintSet.clear(R.id.player_quick_action_view, ConstraintSet.START);
            constraintSet.clear(R.id.player_quick_action_view, ConstraintSet.TOP);
            constraintSet.clear(R.id.player_quick_action_view, ConstraintSet.END);
            constraintSet.clear(R.id.player_quick_action_view, ConstraintSet.BOTTOM);
            constraintSet.constrainWidth(R.id.player_quick_action_view, 0);
            int heightInPx = getResources().getDimensionPixelSize(R.dimen.now_playing_bottom_peek_height);
            constraintSet.constrainHeight(R.id.player_quick_action_view, heightInPx);
            constraintSet.connect(R.id.player_quick_action_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(R.id.player_quick_action_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            constraintSet.connect(R.id.player_quick_action_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            constraintSet.setVisibility(R.id.player_quick_action_view, View.VISIBLE);

            constraintSet.connect(R.id.player_play_pause_placeholder_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.setMargin(R.id.player_play_pause_placeholder_view, ConstraintSet.TOP, 0);

            constraintSet.connect(R.id.placeholder_view_left, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);

        }

        TransitionManager.beginDelayedTransition(constraintLayout);
        constraintSet.applyTo(constraintLayout);
    }


    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();

                bind.nowPlayingMediaControllerView.setPlayer(mediaBrowser);
                assert mediaBrowser != null;
                mediaBrowser.setShuffleModeEnabled(Preferences.isShuffleModeEnabled());
                mediaBrowser.setRepeatMode(Preferences.getRepeatMode());
                setMediaControllerListener(mediaBrowser);
            } catch (Exception e) {
                Log.e(TAG, "bindMediaController execution failed", e);
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaControllerListener(MediaBrowser mediaBrowser) {
        setMediaControllerUI(mediaBrowser);
        setMetadata(mediaBrowser.getMediaMetadata());
        setMediaInfo(mediaBrowser.getMediaMetadata());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setMediaControllerUI(mediaBrowser);
                setMetadata(mediaMetadata);
                setMediaInfo(mediaMetadata);
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                Preferences.setShuffleModeEnabled(shuffleModeEnabled);
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                Preferences.setRepeatMode(repeatMode);
            }
        });
    }

    private void setMetadata(MediaMetadata mediaMetadata) {
        playerMediaTitleLabel.setText(String.valueOf(mediaMetadata.title));
        playerArtistNameLabel.setText(
                mediaMetadata.artist != null
                        ? String.valueOf(mediaMetadata.artist)
                        : mediaMetadata.extras != null && Objects.equals(mediaMetadata.extras.getString("type"), Constants.MEDIA_TYPE_RADIO)
                        ? mediaMetadata.extras.getString("uri", getString(R.string.label_placeholder))
                        : "");

        playerMediaTitleLabel.setSelected(true);
        playerArtistNameLabel.setSelected(true);

        playerMediaTitleLabel.setVisibility(mediaMetadata.title != null && !Objects.equals(mediaMetadata.title, "") ? View.VISIBLE : View.GONE);
        playerArtistNameLabel.setVisibility(
                (mediaMetadata.artist != null && !Objects.equals(mediaMetadata.artist, ""))
                        || mediaMetadata.extras != null && Objects.equals(mediaMetadata.extras.getString("type"), Constants.MEDIA_TYPE_RADIO) && mediaMetadata.extras.getString("uri") != null
                        ? View.VISIBLE
                        : View.GONE);

        updateAssetLinkChips(mediaMetadata);
    }

    private void setMediaInfo(MediaMetadata mediaMetadata) {
        if (mediaMetadata.extras != null) {
            String extension = mediaMetadata.extras.getString("suffix", getString(R.string.player_unknown_format));
            String bitrate = mediaMetadata.extras.getInt("bitrate", 0) != 0 ? mediaMetadata.extras.getInt("bitrate", 0) + "kbps" : "Original";
            String samplingRate = mediaMetadata.extras.getInt("samplingRate", 0) != 0 ? new DecimalFormat("0.#").format(mediaMetadata.extras.getInt("samplingRate", 0) / 1000.0) + "kHz" : "";
            String bitDepth = mediaMetadata.extras.getInt("bitDepth", 0) != 0 ? mediaMetadata.extras.getInt("bitDepth", 0) + "b" : "";

            playerMediaExtension.setText(extension);

            if (bitrate.equals("Original")) {
                playerMediaBitrate.setVisibility(View.GONE);
            } else {
                List<String> mediaQualityItems = new ArrayList<>();

                if (!bitrate.trim().isEmpty()) mediaQualityItems.add(bitrate);
                if (!bitDepth.trim().isEmpty()) mediaQualityItems.add(bitDepth);
                if (!samplingRate.trim().isEmpty()) mediaQualityItems.add(samplingRate);

                String mediaQuality = TextUtils.join(" • ", mediaQualityItems);
                playerMediaBitrate.setVisibility(View.VISIBLE);
                playerMediaBitrate.setText(mediaQuality);
            }
        }

        boolean isTranscodingExtension = !MusicUtil.getTranscodingFormatPreference().equals("raw");
        boolean isTranscodingBitrate = !MusicUtil.getBitratePreference().equals("0");

        if (isTranscodingExtension || isTranscodingBitrate) {
            playerMediaExtension.setText(MusicUtil.getTranscodingFormatPreference() + " (" + getString(R.string.player_transcoding) + ")");
            playerMediaBitrate.setText(!MusicUtil.getBitratePreference().equals("0") ? MusicUtil.getBitratePreference() + "kbps" : getString(R.string.player_transcoding_requested));
        }

        playerTrackInfo.setOnClickListener(view -> {
            TrackInfoDialog dialog = new TrackInfoDialog(mediaMetadata);
            dialog.show(activity.getSupportFragmentManager(), null);
        });
    }

    private void updateAssetLinkChips(MediaMetadata mediaMetadata) {
        if (assetLinkChipGroup == null) return;
        String mediaType = mediaMetadata.extras != null ? mediaMetadata.extras.getString("type", Constants.MEDIA_TYPE_MUSIC) : Constants.MEDIA_TYPE_MUSIC;
        if (!Constants.MEDIA_TYPE_MUSIC.equals(mediaType)) {
            clearAssetLinkChip(playerSongLinkChip);
            clearAssetLinkChip(playerAlbumLinkChip);
            clearAssetLinkChip(playerArtistLinkChip);
            syncAssetLinkGroupVisibility();
            return;
        }

        String songId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("id") : null;
        String albumId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("albumId") : null;
        String artistId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("artistId") : null;

        AssetLinkUtil.AssetLink songLink = bindAssetLinkChip(playerSongLinkChip, AssetLinkUtil.TYPE_SONG, songId);
        AssetLinkUtil.AssetLink albumLink = bindAssetLinkChip(playerAlbumLinkChip, AssetLinkUtil.TYPE_ALBUM, albumId);
        AssetLinkUtil.AssetLink artistLink = bindAssetLinkChip(playerArtistLinkChip, AssetLinkUtil.TYPE_ARTIST, artistId);
        bindAssetLinkView(playerMediaTitleLabel, songLink);
        bindAssetLinkView(playerArtistNameLabel, artistLink != null ? artistLink : songLink);
        bindAssetLinkView(playerMediaCoverViewPager, songLink);
        syncAssetLinkGroupVisibility();
    }

    private AssetLinkUtil.AssetLink bindAssetLinkChip(Chip chip, String type, String id) {
        if (chip == null) return null;
        if (TextUtils.isEmpty(id)) {
            clearAssetLinkChip(chip);
            return null;
        }

        String label = getString(AssetLinkUtil.getLabelRes(type));
        AssetLinkUtil.AssetLink assetLink = AssetLinkUtil.buildAssetLink(type, id);
        if (assetLink == null) {
            clearAssetLinkChip(chip);
            return null;
        }

        chip.setText(getString(R.string.asset_link_chip_text, label, assetLink.id()));
        chip.setVisibility(View.VISIBLE);

        chip.setOnClickListener(v -> {
            activity.openAssetLink(assetLink);
        });

        chip.setOnLongClickListener(v -> {
            AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
            Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, id), Toast.LENGTH_SHORT).show();
            return true;
        });

        return assetLink;
    }

    private void clearAssetLinkChip(Chip chip) {
        if (chip == null) return;
        chip.setVisibility(View.GONE);
        chip.setText("");
        chip.setOnClickListener(null);
        chip.setOnLongClickListener(null);
    }

    private void bindAssetLinkView(View view, AssetLinkUtil.AssetLink assetLink) {
        if (view == null) return;
        if (assetLink == null) {
            AssetLinkUtil.clearLinkAppearance(view);
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            view.setClickable(false);
            view.setLongClickable(false);
            return;
        }

        view.setClickable(true);
        view.setLongClickable(true);
        AssetLinkUtil.applyLinkAppearance(view);
        view.setOnClickListener(v -> {
            boolean collapse = !AssetLinkUtil.TYPE_SONG.equals(assetLink.type());
            activity.openAssetLink(assetLink, collapse);
        });
        view.setOnLongClickListener(v -> {
            AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
            Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, assetLink.id()), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void syncAssetLinkGroupVisibility() {
        if (assetLinkChipGroup == null) return;
        boolean hasVisible = false;
        for (int i = 0; i < assetLinkChipGroup.getChildCount(); i++) {
            View child = assetLinkChipGroup.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                hasVisible = true;
                break;
            }
        }
        assetLinkChipGroup.setVisibility(hasVisible ? View.VISIBLE : View.GONE);
    }

    private void setMediaControllerUI(MediaBrowser mediaBrowser) {
        initPlaybackSpeedButton(mediaBrowser);

        if (mediaBrowser.getMediaMetadata().extras != null) {
            switch (mediaBrowser.getMediaMetadata().extras.getString("type", Constants.MEDIA_TYPE_MUSIC)) {
                case Constants.MEDIA_TYPE_PODCAST:
                    bind.getRoot().setShowShuffleButton(false);
                    bind.getRoot().setShowRewindButton(true);
                    bind.getRoot().setShowPreviousButton(false);
                    bind.getRoot().setShowNextButton(false);
                    bind.getRoot().setShowFastForwardButton(true);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    bind.getRoot().findViewById(R.id.player_playback_speed_button).setVisibility(View.VISIBLE);
                    bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button).setVisibility(View.VISIBLE);
                    bind.getRoot().findViewById(R.id.button_favorite).setVisibility(View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_RADIO:
                    bind.getRoot().setShowShuffleButton(false);
                    bind.getRoot().setShowRewindButton(false);
                    bind.getRoot().setShowPreviousButton(false);
                    bind.getRoot().setShowNextButton(false);
                    bind.getRoot().setShowFastForwardButton(false);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    bind.getRoot().findViewById(R.id.player_playback_speed_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.button_favorite).setVisibility(View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_MUSIC:
                default:
                    bind.getRoot().setShowShuffleButton(true);
                    bind.getRoot().setShowRewindButton(false);
                    bind.getRoot().setShowPreviousButton(true);
                    bind.getRoot().setShowNextButton(true);
                    bind.getRoot().setShowFastForwardButton(false);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL | RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
                    bind.getRoot().findViewById(R.id.player_playback_speed_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.button_favorite).setVisibility(View.VISIBLE);
                    resetPlaybackParameters(mediaBrowser);
                    break;
            }
        }
    }

    private void initCoverLyricsSlideView() {
        playerMediaCoverViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        playerMediaCoverViewPager.setAdapter(new PlayerControllerHorizontalPager(this));

        playerMediaCoverViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");

                if (position == 0) {
                    activity.setBottomSheetDraggableState(true);

                    if (playerBottomSheetFragment != null) {
                        playerBottomSheetFragment.setPlayerControllerVerticalPagerDraggableState(true);
                    }
                } else if (position == 1) {
                    activity.setBottomSheetDraggableState(false);

                    if (playerBottomSheetFragment != null) {
                        playerBottomSheetFragment.setPlayerControllerVerticalPagerDraggableState(false);
                    }
                }
            }
        });
    }

    private void initMediaListenable() {
        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), media -> {
            if (media != null) {
                Log.d(TAG, "observeLiveMedia in playerBottomSheetViewModel");
                ratingViewModel.setSong(media);
                buttonFavorite.setChecked(media.getStarred() != null);
                buttonFavorite.setOnClickListener(v -> playerBottomSheetViewModel.setFavorite(requireContext(), media));
                buttonFavorite.setOnLongClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.TRACK_OBJECT, media);

                    RatingDialog dialog = new RatingDialog();
                    dialog.setArguments(bundle);
                    dialog.show(requireActivity().getSupportFragmentManager(), null);


                    return true;
                });

                Integer currentRating = media.getUserRating();

                songRatingBar.setRating(Objects.requireNonNullElse(currentRating, 0));

                songRatingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
                    if (fromUser) {
                        ratingViewModel.rate((int) rating);
                        media.setUserRating((int) rating);
                    }
                });


                if (getActivity() != null) {
                    playerBottomSheetViewModel.refreshMediaInfo(requireActivity(), media);
                }
            }
        });
    }

    private void initMediaLabelButton() {
        playerBottomSheetViewModel.getLiveAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null) {
                playerMediaTitleLabel.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                    NavHostFragment.findNavController(this).navigate(R.id.albumPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
            }
        });
    }

    private void initArtistLabelButton() {
        playerBottomSheetViewModel.getLiveArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                playerArtistNameLabel.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                    NavHostFragment.findNavController(this).navigate(R.id.artistPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
            }
        });
    }

    private void initPlaybackSpeedButton(MediaBrowser mediaBrowser) {
        playbackSpeedButton.setOnClickListener(view -> {
            float currentSpeed = Preferences.getPlaybackSpeed();

            if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_080) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_100));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_100));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_100);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_100) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_125));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_125));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_125);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_125) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_150));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_150));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_150);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_150) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_175));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_175));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_175);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_175) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_200));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_200));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_200);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_200) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_080));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_080));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_080);
            }
        });

        skipSilenceToggleButton.setOnClickListener(view -> {
            Preferences.setSkipSilenceMode(!skipSilenceToggleButton.isChecked());
        });
    }

    private void initEqualizerButton() {
        equalizerButton.setOnClickListener(v -> {
            NavController navController = NavHostFragment.findNavController(this);
            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.equalizerFragment, true)
                    .build();
            navController.navigate(R.id.equalizerFragment, null, navOptions);
            if (activity != null) activity.collapseBottomSheetDelayed();
        });
    }

    public void goToControllerPage() {
        playerMediaCoverViewPager.setCurrentItem(0, false);
    }

    public void goToLyricsPage() {
        playerMediaCoverViewPager.setCurrentItem(1, true);
    }

    private void checkAndSetRatingContainerVisibility() {
     if (ratingContainer == null) return;

     if (Preferences.showItemStarRating()) {
            ratingContainer.setVisibility(View.VISIBLE);
         }
     else {
            ratingContainer.setVisibility(View.GONE);
        }
    }

    private void setPlaybackParameters(MediaBrowser mediaBrowser) {
        Button playbackSpeedButton = bind.getRoot().findViewById(R.id.player_playback_speed_button);
        float currentSpeed = Preferences.getPlaybackSpeed();
        boolean skipSilence = Preferences.isSkipSilenceMode();

        mediaBrowser.setPlaybackParameters(new PlaybackParameters(currentSpeed));
        playbackSpeedButton.setText(getString(R.string.player_playback_speed, currentSpeed));

        // TODO Skippare il silenzio
        skipSilenceToggleButton.setChecked(skipSilence);
    }

    private void resetPlaybackParameters(MediaBrowser mediaBrowser) {
        mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_100));
        // TODO Resettare lo skip del silenzio
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaServiceBinder = (MediaService.LocalBinder) service;
            isServiceBound = true;
            checkEqualizerBands();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBinder = null;
            isServiceBound = false;
        }
    };

    private void bindMediaService() {
        Intent intent = new Intent(requireActivity(), MediaService.class);
        intent.setAction(MediaService.ACTION_BIND_EQUALIZER);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
    }

    private void checkEqualizerBands() {
        if (mediaServiceBinder != null) {
            EqualizerManager eqManager = mediaServiceBinder.getEqualizerManager();
            short numBands = eqManager.getNumberOfBands();

            if (equalizerButton != null) {
                if (numBands == 0) {
                    equalizerButton.setVisibility(View.GONE);

                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerOpenQueueButton.getLayoutParams();
                    params.startToEnd = ConstraintLayout.LayoutParams.UNSET;
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                    playerOpenQueueButton.setLayoutParams(params);
                } else {
                    equalizerButton.setVisibility(View.VISIBLE);

                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerOpenQueueButton.getLayoutParams();
                    params.startToStart = ConstraintLayout.LayoutParams.UNSET;
                    params.startToEnd = R.id.player_open_equalizer_button;
                    playerOpenQueueButton.setLayoutParams(params);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        bindMediaService();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
