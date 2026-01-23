package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.QueueDao;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QueueRepository {
    private static final String TAG = "QueueRepository";

    private final QueueDao queueDao = AppDatabase.getInstance().queueDao();

    public LiveData<List<Queue>> getLiveQueue() {
        return queueDao.getAll();
    }

    public List<Child> getMedia() {
        List<Child> media = new ArrayList<>();

        GetMediaThreadSafe getMedia = new GetMediaThreadSafe(queueDao);
        Thread thread = new Thread(getMedia);
        thread.start();

        try {
            thread.join();
            media = getMedia.getMedia().stream()
                    .map(Child.class::cast)
                    .collect(Collectors.toList());

        } catch (InterruptedException e) {
            Log.e(TAG, "getMedia", e);
        }

        return media;
    }

    public MutableLiveData<PlayQueue> getPlayQueue() {
        MutableLiveData<PlayQueue> playQueue = new MutableLiveData<>();

        Log.d(TAG, "Getting play queue from server...");

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .getPlayQueue()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlayQueue() != null) {
                            PlayQueue serverQueue = response.body().getSubsonicResponse().getPlayQueue();
                            Log.d(TAG, "Server returned play queue with " +
                                    (serverQueue.getEntries() != null ? serverQueue.getEntries().size() : 0) + " items");
                            playQueue.setValue(serverQueue);
                        } else {
                            Log.d(TAG, "Server returned no play queue");
                            playQueue.setValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Failed to get play queue", t);
                        playQueue.setValue(null);
                    }
                });

        return playQueue;
    }

    public void savePlayQueue(List<String> ids, String current, long position) {
        Log.d(TAG, "Saving play queue to server - Items: " + ids.size() + ", Current: " + current);

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .savePlayQueue(ids, current, position)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Play queue saved successfully");
                        } else {
                            Log.d(TAG, "Play queue save failed with code: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Play queue save failed", t);
                    }
                });
    }

    @Deprecated
    public void insert(boolean reset, int afterIndex,Child media) {
        insert(media, reset, afterIndex);
    }

    public void insert(Child media, boolean reset, int afterIndex) {
        try {
            List<Queue> mediaList = new ArrayList<>();

            if (!reset) {
                GetMediaThreadSafe getMediaThreadSafe = new GetMediaThreadSafe(queueDao);
                Thread getMediaThread = new Thread(getMediaThreadSafe);
                getMediaThread.start();
                getMediaThread.join();

                mediaList = getMediaThreadSafe.getMedia();
            }

            Queue queueItem = new Queue(media);
            mediaList.add(afterIndex, queueItem);

            for (int i = afterIndex; i < mediaList.size(); i++) {
                mediaList.get(i).setTrackOrder(i);
            }

            Thread delete = new Thread(new DeleteAllThreadSafe(queueDao));
            delete.start();
            delete.join();

            Thread insertAll = new Thread(new InsertAllThreadSafe(queueDao, mediaList));
            insertAll.start();
            insertAll.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "insert", e);
        }
    }

    public void insertAll(List<Child> toAdd, boolean reset, int afterIndex) {

        try {
            List<Queue> media;

            if (reset) {
                media = new ArrayList<>(toAdd.size());
                for (int i = 0;i< toAdd.size();i ++ ) {
                    Queue item = new Queue(toAdd.get(i));
                    item.setTrackOrder(i);
                    media.add(item);
                }
            }else {
                GetMediaThreadSafe getMediaThreadSafe = new GetMediaThreadSafe(queueDao);
                Thread getMediaThread = new Thread(getMediaThreadSafe);
                getMediaThread.start();
                getMediaThread.join();

                media = getMediaThreadSafe.getMedia();

                HashSet<String> existingIds = new HashSet<>(toAdd.size());
                List<Queue> newAddItems = new ArrayList<>(toAdd.size());

                int trackIndex = afterIndex;
                for(Child item :toAdd){
                    existingIds.add(item.getId());
                    Queue queue = new Queue(item);
                    queue.setTrackOrder(trackIndex);
                    trackIndex ++ ;
                    newAddItems.add(queue);
                }

                List<Queue> tails = new ArrayList<>(media.size() - afterIndex);
                for (int i = afterIndex;i< media.size();i++){
                    if(!existingIds.contains(media.get(i).getId())){
                        media.get(i).setTrackOrder(trackIndex);
                        trackIndex ++ ;
                        tails.add(media.get(i));
                    }
                }

                media.subList(afterIndex, media.size()).clear();
                media.addAll(newAddItems);
                media.addAll(tails);
            }

            for(Queue q:media){
                Log.d(TAG, "insertAll: " + q.getId()  +">-"+ q.getTrackOrder()+">-"+ q.getTitle());
            }

            Thread delete = new Thread(new DeleteAllThreadSafe(queueDao));
            delete.start();
            delete.join();

            Thread insertAll = new Thread(new InsertAllThreadSafe(queueDao, media));
            insertAll.start();
            insertAll.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "insertAll", e);
        }
    }

    public void delete(int position) {
        DeleteThreadSafe delete = new DeleteThreadSafe(queueDao, position);
        Thread thread = new Thread(delete);
        thread.start();
    }

    public void deleteAll() {
        DeleteAllThreadSafe deleteAll = new DeleteAllThreadSafe(queueDao);
        Thread thread = new Thread(deleteAll);
        thread.start();
    }

    public int count() {
        int count = 0;

        CountThreadSafe countThread = new CountThreadSafe(queueDao);
        Thread thread = new Thread(countThread);
        thread.start();

        try {
            thread.join();
            count = countThread.getCount();
        } catch (InterruptedException e) {
            Log.e(TAG, "count", e);
        }

        return count;
    }

    public void setLastPlayedTimestamp(String id) {
        SetLastPlayedTimestampThreadSafe timestamp = new SetLastPlayedTimestampThreadSafe(queueDao, id);
        Thread thread = new Thread(timestamp);
        thread.start();
    }

    public void setPlayingPausedTimestamp(String id, long ms) {
        SetPlayingPausedTimestampThreadSafe timestamp = new SetPlayingPausedTimestampThreadSafe(queueDao, id, ms);
        Thread thread = new Thread(timestamp);
        thread.start();
    }

    public int getLastPlayedMediaIndex() {
        int index = 0;

        GetLastPlayedMediaThreadSafe getLastPlayedMediaThreadSafe = new GetLastPlayedMediaThreadSafe(queueDao);
        Thread thread = new Thread(getLastPlayedMediaThreadSafe);
        thread.start();

        try {
            thread.join();
            Queue lastMediaPlayed = getLastPlayedMediaThreadSafe.getQueueItem();
            if (lastMediaPlayed == null) return 0;
            index = lastMediaPlayed.getTrackOrder();
        } catch (InterruptedException e) {
            Log.e(TAG, "getLastPlayedMediaIndex", e);
        }

        return index;
    }

    public long getLastPlayedMediaTimestamp() {
        long timestamp = 0;

        GetLastPlayedMediaThreadSafe getLastPlayedMediaThreadSafe = new GetLastPlayedMediaThreadSafe(queueDao);
        Thread thread = new Thread(getLastPlayedMediaThreadSafe);
        thread.start();

        try {
            thread.join();
            Queue lastMediaPlayed = getLastPlayedMediaThreadSafe.getQueueItem();
            if (lastMediaPlayed == null){
                return 0;
            }
            timestamp = lastMediaPlayed.getPlayingChanged();
        } catch (InterruptedException e) {
            Log.e(TAG, "getLastPlayedMediaTimestamp", e);
        }

        return timestamp;
    }

    private static class GetMediaThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private List<Queue> media;

        public GetMediaThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            media = queueDao.getAllSimple();
        }

        public List<Queue> getMedia() {
            return media;
        }
    }

    private static class InsertAllThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private final List<Queue> media;

        public InsertAllThreadSafe(QueueDao queueDao, List<Queue> media) {
            this.queueDao = queueDao;
            this.media = media;
        }

        @Override
        public void run() {
            queueDao.insertAll(media);
        }
    }

    private static class DeleteThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private final int position;

        public DeleteThreadSafe(QueueDao queueDao, int position) {
            this.queueDao = queueDao;
            this.position = position;
        }

        @Override
        public void run() {
            queueDao.delete(position);
        }
    }

    private static class DeleteAllThreadSafe implements Runnable {
        private final QueueDao queueDao;

        public DeleteAllThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            queueDao.deleteAll();
        }
    }

    private static class CountThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private int count = 0;

        public CountThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            count = queueDao.count();
        }

        public int getCount() {
            return count;
        }
    }

    private static class SetLastPlayedTimestampThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private final String mediaId;

        public SetLastPlayedTimestampThreadSafe(QueueDao queueDao, String mediaId) {
            this.queueDao = queueDao;
            this.mediaId = mediaId;
        }

        @Override
        public void run() {
            queueDao.setLastPlay(mediaId, System.currentTimeMillis());
        }
    }

    private static class SetPlayingPausedTimestampThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private final String mediaId;
        private final long ms;

        public SetPlayingPausedTimestampThreadSafe(QueueDao queueDao, String mediaId, long ms) {
            this.queueDao = queueDao;
            this.mediaId = mediaId;
            this.ms = ms;
        }

        @Override
        public void run() {
            queueDao.setPlayingChanged(mediaId, ms);
        }
    }

    private static class GetLastPlayedMediaThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private Queue lastMediaPlayed;

        public GetLastPlayedMediaThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            lastMediaPlayed = queueDao.getLastPlayed();
        }

        public Queue getQueueItem() {
            return lastMediaPlayed;
        }
    }
}
