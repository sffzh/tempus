package com.cappielloantonio.tempo.service;

import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.os.BundleCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.interfaces.MediaIndexCallback;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.repository.ChronologyRepository;
import com.cappielloantonio.tempo.repository.QueueRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MediaManager {
    private static final String TAG = "MediaManager";
    private static WeakReference<MediaBrowser> attachedBrowserRef = new WeakReference<>(null);
    public static AtomicBoolean justStarted = new AtomicBoolean(false);

    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public static List<Child> getTracks(Bundle bundle){
        if (bundle == null)return null;
        return BundleCompat.getParcelableArrayList(bundle, Constants.TRACKS_OBJECT, Child.class);
    }

    public static Child getTrackItem(Bundle bundle) {
        if (bundle == null)return null;
        ArrayList<Child> parcelableArrayList = BundleCompat.getParcelableArrayList(bundle, Constants.TRACKS_OBJECT, Child.class);
        if (parcelableArrayList != null){
            return parcelableArrayList.get(bundle.getInt(Constants.ITEM_POSITION));
        }
        return null;
    }

    public static void registerPlaybackObserver(
            ListenableFuture<MediaBrowser> browserFuture,
            PlaybackViewModel playbackViewModel
    ) {
        if (browserFuture == null) return;

        Futures.addCallback(browserFuture, new FutureCallback<MediaBrowser>() {
            @Override
            public void onSuccess(MediaBrowser browser) {
                MediaBrowser current = attachedBrowserRef.get();
                if (current != browser) {
                    browser.addListener(new Player.Listener() {
                        @Override
                        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                            Log.d(TAG, "onEvent start, events = " + events);
                            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                                    || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                                    || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {

                                String mediaId = player.getCurrentMediaItem() != null
                                        ? player.getCurrentMediaItem().mediaId
                                        : null;

                                boolean playing = player.getPlaybackState() == Player.STATE_READY
                                        && player.getPlayWhenReady();

                                playbackViewModel.update(mediaId, playing);
                            }
                        }
                    });

                    String mediaId = browser.getCurrentMediaItem() != null
                            ? browser.getCurrentMediaItem().mediaId
                            : null;
                    boolean playing = browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady();
                    playbackViewModel.update(mediaId, playing);

                    attachedBrowserRef = new WeakReference<>(browser);
                } else {
                    String mediaId = browser.getCurrentMediaItem() != null
                            ? browser.getCurrentMediaItem().mediaId
                            : null;
                    boolean playing = browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady();
                    playbackViewModel.update(mediaId, playing);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Failed to get MediaBrowser instance", t);
            }
        }, MoreExecutors.directExecutor());
    }

    public static void onBrowserReleased(@Nullable MediaBrowser released) {
        MediaBrowser attached = attachedBrowserRef.get();
        if (attached == released) {
            attachedBrowserRef.clear();
        }
    }

    public static void reset(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        assert mediaBrowser != null;
                        if (mediaBrowser.isPlaying()) {
                            mediaBrowser.pause();
                        }
                        mediaBrowser.stop();
                        mediaBrowser.clearMediaItems();
                        clearDatabase();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "hide", e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void hide(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        assert mediaBrowser != null;
                        if (mediaBrowser.isPlaying()) {
                            mediaBrowser.pause();
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "hide", e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void check(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (Objects.requireNonNull(mediaBrowserListenableFuture.get()).getMediaItemCount() < 1) {
                            List<Child> media = getQueueRepository().getMedia();
                            if (media != null && !media.isEmpty()) {
                                init(mediaBrowserListenableFuture, media);
                            }
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "check", e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static MediaBrowserHandler checkHandler(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture){
        return (f, browser)->{
            if (browser.getMediaItemCount() < 1) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    List<Child> media = getQueueRepository().getMedia();
                    if (media != null && !media.isEmpty()) {
                        init(mediaBrowserListenableFuture, media);
                    }
                });
            }
        };
    }

    public static void checkAsync(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture){
        Log.d(TAG, "checkAsync is starting.");
        addListener(mediaBrowserListenableFuture, (f, browser) -> {
            Log.d(TAG, "checkAsync listener is added.browser.getMediaItemCount() =" + browser.getMediaItemCount() );
            if (browser.getMediaItemCount() < 1) {
                // 1. 先在后台线程查询数据库
                backgroundExecutor.execute(() -> {
                    List<Child> media = getQueueRepository().getMedia();
                    int lastIndex = getQueueRepository().getLastPlayedMediaIndex();
                    long lastPos = getQueueRepository().getLastPlayedMediaTimestamp();
                    // 2. 查询完后再切回主线程更新播放器
                    if (media != null && !media.isEmpty()) {
                        List<MediaItem> mediaItems = MappingUtil.mapMediaItems(media);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            browser.setMediaItems(mediaItems);
                            browser.seekTo(lastIndex, lastPos);
                            browser.prepare();
                            Log.d(TAG, "brower is prepared.");
                        });
                    }
                });
            }
        });
    }

    public static void init(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        assert mediaBrowser != null;
                        mediaBrowser.clearMediaItems();
                        mediaBrowser.setMediaItems(MappingUtil.mapMediaItems(media));
                        mediaBrowser.seekTo(getQueueRepository().getLastPlayedMediaIndex(), getQueueRepository().getLastPlayedMediaTimestamp());
                        mediaBrowser.prepare();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "init", e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public interface MediaBrowserHandler{
        void run(@NonNull ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, @NonNull MediaBrowser mediaBrowser);
    }

    public static void addListener(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaBrowserHandler... runnable) {
        if (mediaBrowserListenableFuture == null) return;
        mediaBrowserListenableFuture.addListener(()->{
            try {
                if (mediaBrowserListenableFuture.isDone()) {
                    MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                    assert mediaBrowser != null;
                    for (MediaBrowserHandler work: runnable){
                        work.run(mediaBrowserListenableFuture, mediaBrowser);
                    }
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "addListener", e);
            }
        }, MoreExecutors.directExecutor());
    }

    public static void disableShuffle(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture){
        addListener(mediaBrowserListenableFuture, (future, mediaBrowser)->{
            Preferences.setShuffleModeEnabled(false);
            mediaBrowser.setShuffleModeEnabled(false);
        });
    }

    public static MediaBrowserHandler startQueueHandler(int startIndex, List<Child> medias){
        return (future, browser)->{

            if (medias == null) return;
            if (medias.size() == 1){
                browser.setMediaItem(MappingUtil.mapMediaItem(medias.get(0)));
                browser.prepare();
                browser.play();
                enqueueDatabase(medias.get(0));
                getQueueRepository().insert(medias.get(0), true, 0);
                return;
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                justStarted.set(true);
                browser.setMediaItems(MappingUtil.mapMediaItems(medias), startIndex, 0);
                browser.prepare();

                Player.Listener timelineListener = new Player.Listener() {
                    @Override
                    public void onTimelineChanged(@NonNull Timeline timeline, int reason) {

                        int itemCount = browser.getMediaItemCount();
                        if (startIndex >= 0 && startIndex < itemCount) {
                            browser.seekTo(startIndex, 0);
                            browser.play();
                            browser.removeListener(this);
                        } else {
                            Log.d(TAG, "Cannot start playback: itemCount=" + itemCount + ", startIndex=" + startIndex);
                        }
                    }
                };

                browser.addListener(timelineListener);
            });

            backgroundExecutor.execute(() -> {
                Log.d(TAG, "Background: enqueuing to database");
                getQueueRepository().insertAll(medias, true, 0);
            });
        };
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex) {
        if (mediaBrowserListenableFuture != null) {

            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        final MediaBrowser browser = mediaBrowserListenableFuture.get();
                        final List<MediaItem> items = MappingUtil.mapMediaItems(media);

                        new Handler(Looper.getMainLooper()).post(() -> {
                            justStarted.set(true);
                            assert browser != null;
                            browser.setMediaItems(items, startIndex, 0);
                            browser.prepare();

                            Player.Listener timelineListener = new Player.Listener() {
                                @Override
                                public void onTimelineChanged(@NonNull Timeline timeline, int reason) {

                                    int itemCount = browser.getMediaItemCount();
                                    if (startIndex >= 0 && startIndex < itemCount) {
                                        browser.seekTo(startIndex, 0);
                                        browser.play();
                                        browser.removeListener(this);
                                    } else {
                                        Log.d(TAG, "Cannot start playback: itemCount=" + itemCount + ", startIndex=" + startIndex);
                                    }
                                }
                            };

                            browser.addListener(timelineListener);
                        });

                        backgroundExecutor.execute(() -> {
                            Log.d(TAG, "Background: enqueuing to database");
                            getQueueRepository().insertAll(media, true, 0);
                        });
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "startQueue with list", e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        assert browser != null;
                        browser.setMediaItem(MappingUtil.mapMediaItem(media));
                        browser.prepare();
                        browser.play();
                        getQueueRepository().insert(media,true,0);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "startQueue", e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void playDownloadedMediaItem(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaItem mediaItem) {
        if (mediaBrowserListenableFuture != null && mediaItem != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        assert mediaBrowser != null;
                        mediaBrowser.setMediaItem(mediaItem);
                        mediaBrowser.prepare();
                        mediaBrowser.play();
                        clearDatabase();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "playDownloadedMediaItem",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startRadio(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, InternetRadioStation internetRadioStation) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        assert browser != null;
                        browser.setMediaItem(MappingUtil.mapInternetRadioStation(internetRadioStation));
                        browser.prepare();
                        browser.play();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "startPodcast",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startPodcast(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, PodcastEpisode podcastEpisode) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        if (browser == null) return;
                        justStarted.set(true);
                        browser.setMediaItem(MappingUtil.mapMediaItem(podcastEpisode));
                        browser.prepare();
                        browser.play();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "startPodcast",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, boolean playImmediatelyAfter, boolean switchImmediately) {
        addListener(mediaBrowserListenableFuture, (f,browser)->{
            if (playImmediatelyAfter && browser.getNextMediaItemIndex() != -1) {
                getQueueRepository().insertAll(media, false, browser.getNextMediaItemIndex());
                browser.addMediaItems(browser.getNextMediaItemIndex(), MappingUtil.mapMediaItems(media));
            } else {
                getQueueRepository().insertAll(media, false, browser.getMediaItemCount());
                browser.addMediaItems(MappingUtil.mapMediaItems(media));
            }
            if(switchImmediately && browser.hasNextMediaItem()){
                browser.seekToNext();
                browser.prepare();
                browser.play();
            }
        });
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, boolean playImmediatelyAfter, boolean switchImmediately) {
        addListener(mediaBrowserListenableFuture, (f,browser)->{
            if (playImmediatelyAfter && browser.getNextMediaItemIndex() != -1) {
                getQueueRepository().insert(media, false, browser.getNextMediaItemIndex());
                browser.addMediaItem(browser.getNextMediaItemIndex(), MappingUtil.mapMediaItem(media));
            } else {
                getQueueRepository().insert(media, false, browser.getMediaItemCount());
                browser.addMediaItem(MappingUtil.mapMediaItem(media));
            }
            if(switchImmediately && browser.hasNextMediaItem()){
                browser.seekToNext();
                browser.prepare();
                browser.play();
            }
        });
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, boolean playImmediatelyAfter) {
        enqueue(mediaBrowserListenableFuture, media, playImmediatelyAfter, false);
    }
    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, boolean playImmediatelyAfter) {
        enqueue(mediaBrowserListenableFuture, media, playImmediatelyAfter, false);
    }


    public static void shuffle(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex, int endIndex) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.d(TAG, "shuffle");
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        assert browser != null;
                        browser.removeMediaItems(startIndex, endIndex + 1);
                        browser.addMediaItems(MappingUtil.mapMediaItems(media).subList(startIndex, endIndex + 1));
                        swapDatabase(media);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "shuffle",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void swap(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int from, int to) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.d(TAG, "swap");
                        Objects.requireNonNull(mediaBrowserListenableFuture.get()).moveMediaItem(from, to);
                        swapDatabase(media);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "swap",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void remove(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "remove");
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        assert  mediaBrowser != null;
                        if (mediaBrowser.getMediaItemCount() > 1 && mediaBrowser.getCurrentMediaItemIndex() != toRemove) {
                            mediaBrowser.removeMediaItem(toRemove);
                            removeDatabase(media, toRemove);
                        } else {
                            removeDatabase(media, -1);
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "remove medias",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void removeRange(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int fromItem, int toItem) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "remove range");
                        Objects.requireNonNull(mediaBrowserListenableFuture.get()).removeMediaItems(fromItem, toItem);
                        removeRangeDatabase(media, fromItem, toItem);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "remove range",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void getCurrentIndex(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaIndexCallback callback) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        callback.onRecovery(Objects.requireNonNull(mediaBrowserListenableFuture.get()).getCurrentMediaItemIndex());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "getCurrentIndex",e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void setLastPlayedTimestamp(MediaItem mediaItem) {
        if (mediaItem != null) getQueueRepository().setLastPlayedTimestamp(mediaItem.mediaId);
    }

    public static void setPlayingPausedTimestamp(MediaItem mediaItem, long ms) {
        if (mediaItem != null)
            getQueueRepository().setPlayingPausedTimestamp(mediaItem.mediaId, ms);
    }

    public static void scrobble(MediaItem mediaItem, boolean submission) {
        if (mediaItem != null && Preferences.isScrobblingEnabled()) {
            assert mediaItem.mediaMetadata.extras != null;
            getSongRepository().scrobble(mediaItem.mediaMetadata.extras.getString("id"), submission);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void continuousPlay(MediaItem mediaItem) {
        if (mediaItem != null && Preferences.isContinuousPlayEnabled() && Preferences.isInstantMixUsable()) {
            Preferences.setLastInstantMix();

            LiveData<List<Child>> instantMix = getSongRepository().getContinuousMix(mediaItem.mediaId,25);

            instantMix.observeForever(new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> media) {
                    if (media != null) {
                        Log.e(TAG, "continuous play");
                        ListenableFuture<MediaBrowser> mediaBrowserListenableFuture = new MediaBrowser.Builder(
                                App.getContext(),
                                new SessionToken(App.getContext(), new ComponentName(App.getContext(), MediaService.class))
                        ).buildAsync();

                        enqueue(mediaBrowserListenableFuture, media, true, false);
                    }

                    instantMix.removeObserver(this);
                }
            });
        }
    }

    public static void saveChronology(MediaItem mediaItem) {
        if (mediaItem != null) {
            getChronologyRepository().insert(new Chronology(mediaItem));
        }
    }

    private static QueueRepository getQueueRepository() {
        return new QueueRepository();
    }

    private static SongRepository getSongRepository() {
        return new SongRepository();
    }

    private static ChronologyRepository getChronologyRepository() {
        return new ChronologyRepository();
    }

    private static void enqueueDatabase(Child media) {
        getQueueRepository().insert(media, true, 0);
    }

    private static void swapDatabase(List<Child> media) {
        getQueueRepository().insertAll(media, true, 0);
    }

    private static void removeDatabase(List<Child> media, int toRemove) {
        if (toRemove != -1) {
            media.remove(toRemove);
            getQueueRepository().insertAll(media, true, 0);
        }
    }

    private static void removeRangeDatabase(List<Child> media, int fromItem, int toItem) {
        List<Child> toRemove = media.subList(fromItem, toItem);

        media.removeAll(toRemove);

        getQueueRepository().insertAll(media, true, 0);
    }

    public static void clearDatabase() {
        getQueueRepository().deleteAll();
    }
}
