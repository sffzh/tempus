package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.github.models.LatestRelease;
import com.cappielloantonio.tempo.repository.QueueRepository;
import com.cappielloantonio.tempo.repository.SystemRepository;
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public boolean isQueueLoaded() {
        QueueRepository queueRepository = new QueueRepository();
        return queueRepository.count() != 0;
    }

    public LiveData<SubsonicResponse> ping() {
        return SystemRepository.INSTANCE.ping();
    }

    public LiveData<LatestRelease> checkTempoUpdate() {
        return SystemRepository.INSTANCE.checkTempoUpdate();
    }
}
