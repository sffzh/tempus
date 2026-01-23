package com.cappielloantonio.tempo;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.cappielloantonio.tempo.github.Github;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.subsonic.Subsonic;
import com.cappielloantonio.tempo.subsonic.SubsonicPreferences;
import com.cappielloantonio.tempo.util.Preferences;

import cn.sffzh.tempus.navidrome.Navidrome;

public class App extends Application {
    private static App instance;
    private static Subsonic subsonic;

    private static Navidrome naviDrome;

    private static Github github;

    @Override
    public void onCreate() {
        super.onCreate();
        // 1. 关键：指向系统创建的实例
        instance = this;

//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Preferences.migrateSharedPreferences(androidx.preference.PreferenceManager.getDefaultSharedPreferences(App.getInstance()));
        ThemeHelper.applyTheme(Preferences.getTheme(ThemeHelper.DEFAULT_MODE));
    }

    public static App getInstance() {
        return instance;
    }

    public static Context getContext() {
        return instance;
    }

    public static Subsonic getSubsonicClientInstance(boolean override) {
        if (subsonic == null || override) {
            subsonic = getSubsonicClient();
        }
        return subsonic;
    }

    public static Github getGithubClientInstance() {
        if (github == null) {
            github = new Github();
        }
        return github;
    }

    public static void refreshSubsonicClient() {
        subsonic = getSubsonicClient();
    }

    private static Subsonic getSubsonicClient() {
        SubsonicPreferences preferences = getSubsonicPreferences();

        if (preferences.getAuthentication() != null) {
            if (preferences.getAuthentication().getPassword() != null)
                Preferences.setPassword(preferences.getAuthentication().getPassword());
            if (preferences.getAuthentication().getToken() != null)
                Preferences.setToken(preferences.getAuthentication().getToken());
            if (preferences.getAuthentication().getSalt() != null)
                Preferences.setSalt(preferences.getAuthentication().getSalt());
        }

        return new Subsonic(preferences);
    }

    @NonNull
    private static SubsonicPreferences getSubsonicPreferences() {
        String server = Preferences.getInUseServerAddress();
        String username = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        String salt = Preferences.getSalt();
        boolean isLowSecurity = Preferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        return preferences;
    }
}
