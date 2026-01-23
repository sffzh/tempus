package com.cappielloantonio.tempo.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.media3.common.Player
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.model.HomeSector
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension
import com.google.gson.Gson


object Preferences {
    const val THEME = "theme"
    private const val SERVER = "server"
    private const val USER = "user"
    private const val PASSWORD = "password"
    private const val TOKEN = "token"
    private const val NAVIDROME_TOKEN = "NAVIDROME_TOKEN"
    private const val SALT = "salt"
    private const val LOW_SECURITY = "low_security"
    private const val BATTERY_OPTIMIZATION = "battery_optimization"
    private const val SERVER_ID = "server_id"
    private const val OPEN_SUBSONIC = "open_subsonic"
    private const val OPEN_SUBSONIC_EXTENSIONS = "open_subsonic_extensions"
    private const val LOCAL_ADDRESS = "local_address"
    private const val IN_USE_SERVER_ADDRESS = "in_use_server_address"
    private const val NEXT_SERVER_SWITCH = "next_server_switch"
    private const val PLAYBACK_SPEED = "playback_speed"
    private const val SKIP_SILENCE = "skip_silence"
    private const val SHUFFLE_MODE = "shuffle_mode"
    private const val REPEAT_MODE = "repeat_mode"
    private const val IMAGE_CACHE_SIZE = "image_cache_size"
    private const val STREAMING_CACHE_SIZE = "streaming_cache_size"
    private const val IMAGE_SIZE = "image_size"
    private const val MAX_BITRATE_WIFI = "max_bitrate_wifi"
    private const val MAX_BITRATE_MOBILE = "max_bitrate_mobile"
    private const val AUDIO_TRANSCODE_FORMAT_WIFI = "audio_transcode_format_wifi"
    private const val AUDIO_TRANSCODE_FORMAT_MOBILE = "audio_transcode_format_mobile"
    private const val WIFI_ONLY = "wifi_only"
    private const val DATA_SAVING_MODE = "data_saving_mode"
    private const val SERVER_UNREACHABLE = "server_unreachable"
    private const val SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE = "sync_starred_artists_for_offline_use"
    private const val SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE = "sync_starred_albums_for_offline_use"
    private const val SYNC_STARRED_TRACKS_FOR_OFFLINE_USE = "sync_starred_tracks_for_offline_use"
    private const val QUEUE_SYNCING = "queue_syncing"
    private const val QUEUE_SYNCING_COUNTDOWN = "queue_syncing_countdown"
    private const val ROUNDED_CORNER = "rounded_corner"
    private const val ROUNDED_CORNER_SIZE = "rounded_corner_size"
    private const val PODCAST_SECTION_VISIBILITY = "podcast_section_visibility"
    private const val RADIO_SECTION_VISIBILITY = "radio_section_visibility"
    private const val AUTO_DOWNLOAD_LYRICS = "auto_download_lyrics"
    private const val MUSIC_DIRECTORY_SECTION_VISIBILITY = "music_directory_section_visibility"
    private const val REPLAY_GAIN_MODE = "replay_gain_mode"
    private const val AUDIO_TRANSCODE_PRIORITY = "audio_transcode_priority"
    private const val STREAMING_CACHE_STORAGE = "streaming_cache_storage"
    private const val DOWNLOAD_STORAGE = "download_storage"
    private const val DOWNLOAD_DIRECTORY_URI = "download_directory_uri"
    private const val DEFAULT_DOWNLOAD_VIEW_TYPE = "default_download_view_type"
    private const val AUDIO_TRANSCODE_DOWNLOAD = "audio_transcode_download"
    private const val AUDIO_TRANSCODE_DOWNLOAD_PRIORITY = "audio_transcode_download_priority"
    private const val MAX_BITRATE_DOWNLOAD = "max_bitrate_download"
    private const val AUDIO_TRANSCODE_FORMAT_DOWNLOAD = "audio_transcode_format_download"
    private const val SHARE = "share"
    private const val SCROBBLING = "scrobbling"
    private const val ESTIMATE_CONTENT_LENGTH = "estimate_content_length"
    private const val BUFFERING_STRATEGY = "buffering_strategy"
    private const val SKIP_MIN_STAR_RATING = "skip_min_star_rating"
    private const val MIN_STAR_RATING = "min_star_rating"
    private const val ALWAYS_ON_DISPLAY = "always_on_display"
    private const val AUDIO_QUALITY_PER_ITEM = "audio_quality_per_item"
    private const val HOME_SECTOR_LIST = "home_sector_list"
    private const val SONG_RATING_PER_ITEM = "song_rating_per_item"
    private const val RATING_PER_ITEM = "rating_per_item"
    private const val NEXT_UPDATE_CHECK = "next_update_check"
    private const val GITHUB_UPDATE_CHECK = "github_update_check"
    private const val CONTINUOUS_PLAY = "continuous_play"
    private const val LAST_INSTANT_MIX = "last_instant_mix"
    private const val ALLOW_PLAYLIST_DUPLICATES = "allow_playlist_duplicates"
    private const val EQUALIZER_ENABLED = "equalizer_enabled"
    private const val EQUALIZER_BAND_LEVELS = "equalizer_band_levels"
    private const val MINI_SHUFFLE_BUTTON_VISIBILITY = "mini_shuffle_button_visibility"
    private const val ALBUM_DETAIL = "album_detail"
    private const val ALBUM_SORT_ORDER = "album_sort_order"
    private const val DEFAULT_ALBUM_SORT_ORDER = Constants.ALBUM_ORDER_BY_NAME
    private const val ARTIST_SORT_BY_ALBUM_COUNT= "artist_sort_by_album_count"
    private const val SORT_SEARCH_CHRONOLOGICALLY= "sort_search_chronologically"
    private const val ARTIST_DISPLAY_BIOGRAPHY= "artist_display_biography"

    private const val CURRENT_PUASED_FLAG = "CURRENT_PUASED_FLAG"

    // 1. 普通配置：存储 UI 状态、非敏感设置
    private val generalPrefs: SharedPreferences by lazy {
        App.getContext().getSharedPreferences("general_settings", Context.MODE_PRIVATE)
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val context = App.getContext()

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Deprecated(
        message = "过渡方法，等所有设备确认完成迁移后即可删除"
    ) //等当前所有设备完成迁移后即删除此方法
    @JvmStatic
    fun migrateSharedPreferences(oldPrefs: SharedPreferences) {
        val allEntries = oldPrefs.all

        if (allEntries.isEmpty()) return
        val sensitiveKeys = listOf(PASSWORD, TOKEN, SALT, NAVIDROME_TOKEN)

        allEntries.forEach { (key, value) ->
            val targetPrefs = if (key in sensitiveKeys) encryptedPrefs else generalPrefs

            targetPrefs.edit().apply {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is Long -> putLong(key, value)
                }
                apply()
            }
        }

        // 迁移完成后清空旧数据，防止下次重复迁移
        oldPrefs.edit { clear() }
    }


    @JvmStatic
    fun isPaused(): Boolean {

        return generalPrefs.getBoolean(CURRENT_PUASED_FLAG, true)
    }

    @JvmStatic
    fun setIsPaused(value: Boolean) {
        generalPrefs.edit { putBoolean(CURRENT_PUASED_FLAG, value) }
    }

    @JvmStatic
    fun getServer(): String? {
        return generalPrefs.getString(SERVER, null)
    }

    @JvmStatic
    fun setServer(server: String?) {
        generalPrefs.edit { putString(SERVER, server) }
    }

    @JvmStatic
    fun getUser(): String? {
        return generalPrefs.getString(USER, null)
    }

    @JvmStatic
    fun setUser(user: String?) {
        generalPrefs.edit { putString(USER, user) }
    }
    /* 使用加密存储的项 */
    @JvmStatic
    fun getPassword(): String? {
        return encryptedPrefs.getString(PASSWORD, null)
    }

    @JvmStatic
    fun setPassword(password: String?) {
        encryptedPrefs.edit { putString(PASSWORD, password) }
//        encryptedPrefs.edit().putString(PASSWORD, password).apply()
    }

    @JvmStatic
    fun getToken(): String? {
        return encryptedPrefs.getString(TOKEN, null)
    }

    @JvmStatic
    fun setToken(token: String?) {
        encryptedPrefs.edit { putString(TOKEN, token) }
    }

    @JvmStatic
    fun getSalt(): String? {
        return encryptedPrefs.getString(SALT, null)
    }

    @JvmStatic
    fun setSalt(salt: String?) {
        encryptedPrefs.edit { putString(SALT, salt) }
    }
    @JvmStatic
    fun getNavToken(): String? {
        return encryptedPrefs.getString(NAVIDROME_TOKEN, null)
    }

    @JvmStatic
    fun setNavToken(token: String?) {
        encryptedPrefs.edit { putString(NAVIDROME_TOKEN, token) }
    }
    /* 使用加密存储的项 */

    @JvmStatic
    fun isLowScurity(): Boolean {
        return generalPrefs.getBoolean(LOW_SECURITY, false)
    }

    @JvmStatic
    fun setLowSecurity(isLowSecurity: Boolean) {
        generalPrefs.edit().putBoolean(LOW_SECURITY, isLowSecurity).apply()
    }

    @JvmStatic
    fun getServerId(): String? {
        return generalPrefs.getString(SERVER_ID, null)
    }

    @JvmStatic
    fun setServerId(serverId: String?) {
        generalPrefs.edit { putString(SERVER_ID, serverId) }
    }

    @JvmStatic
    fun isOpenSubsonic(): Boolean {
        return generalPrefs.getBoolean(OPEN_SUBSONIC, false)
    }

    @JvmStatic
    fun setOpenSubsonic(isOpenSubsonic: Boolean) {
        generalPrefs.edit().putBoolean(OPEN_SUBSONIC, isOpenSubsonic).apply()
    }

    @JvmStatic
    fun getOpenSubsonicExtensions(): String? {
        return generalPrefs.getString(OPEN_SUBSONIC_EXTENSIONS, null)
    }

    @JvmStatic
    fun setOpenSubsonicExtensions(extension: List<OpenSubsonicExtension>) {
        generalPrefs.edit { putString(OPEN_SUBSONIC_EXTENSIONS, Gson().toJson(extension)) }
    }

    @JvmStatic
    fun isAutoDownloadLyricsEnabled(): Boolean {
        val preferences = generalPrefs

        if (preferences.contains(AUTO_DOWNLOAD_LYRICS)) {
            return preferences.getBoolean(AUTO_DOWNLOAD_LYRICS, false)
        }

        return false
    }

    @JvmStatic
    fun setAutoDownloadLyricsEnabled(isEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(AUTO_DOWNLOAD_LYRICS, isEnabled)
        }
    }

    @JvmStatic
    fun getLocalAddress(): String? {
        return generalPrefs.getString(LOCAL_ADDRESS, null)
    }

    @JvmStatic
    fun setLocalAddress(address: String?) {
        generalPrefs.edit { putString(LOCAL_ADDRESS, address) }
    }

    @JvmStatic
    fun getInUseServerAddress(): String? {
        return generalPrefs.getString(IN_USE_SERVER_ADDRESS, null)
            ?.takeIf { it.isNotBlank() }
            ?: getServer()
    }

    @JvmStatic
    fun isInUseServerAddressLocal(): Boolean {
        return getInUseServerAddress() == getLocalAddress()
    }

    @JvmStatic
    fun switchInUseServerAddress() {
        val inUseAddress = if (getInUseServerAddress() == getServer()) getLocalAddress() else getServer()
        generalPrefs.edit { putString(IN_USE_SERVER_ADDRESS, inUseAddress) }
    }

    @JvmStatic
    fun isServerSwitchable(): Boolean {
        return generalPrefs.getLong(
                NEXT_SERVER_SWITCH, 0
        ) + 15000 < System.currentTimeMillis() && !getServer().isNullOrEmpty() && !getLocalAddress().isNullOrEmpty()
    }

    @JvmStatic
    fun setServerSwitchableTimer() {
        generalPrefs.edit().putLong(NEXT_SERVER_SWITCH, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun askForOptimization(): Boolean {
        return generalPrefs.getBoolean(BATTERY_OPTIMIZATION, true)
    }

    @JvmStatic
    fun dontAskForOptimization() {
        generalPrefs.edit { putBoolean(BATTERY_OPTIMIZATION, false) }
    }

    @JvmStatic
    fun getPlaybackSpeed(): Float {
        return generalPrefs.getFloat(PLAYBACK_SPEED, 1f)
    }

    @JvmStatic
    fun setPlaybackSpeed(playbackSpeed: Float) {
        generalPrefs.edit().putFloat(PLAYBACK_SPEED, playbackSpeed).apply()
    }

    @JvmStatic
    fun isSkipSilenceMode(): Boolean {
        return generalPrefs.getBoolean(SKIP_SILENCE, false)
    }

    @JvmStatic
    fun setSkipSilenceMode(isSkipSilenceMode: Boolean) {
        generalPrefs.edit { putBoolean(SKIP_SILENCE, isSkipSilenceMode) }
    }

    @JvmStatic
    fun isShuffleModeEnabled(): Boolean {
        return generalPrefs.getBoolean(SHUFFLE_MODE, false)
    }

    @JvmStatic
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        generalPrefs.edit().putBoolean(SHUFFLE_MODE, shuffleModeEnabled).apply()
    }

    @JvmStatic
    fun getRepeatMode(): Int {
        return generalPrefs.getInt(REPEAT_MODE, Player.REPEAT_MODE_OFF)
    }

    @JvmStatic
    fun setRepeatMode(repeatMode: Int) {
        generalPrefs.edit { putInt(REPEAT_MODE, repeatMode) }
    }

    @JvmStatic
    fun getImageCacheSize(): Int {
        return generalPrefs.getString(IMAGE_CACHE_SIZE, "500")!!.toInt()
    }

    @JvmStatic
    fun getImageSize(): Int {
        return generalPrefs.getString(IMAGE_SIZE, "-1")!!.toInt()
    }

    @JvmStatic
    fun getStreamingCacheSize(): Long {
        return generalPrefs.getString(STREAMING_CACHE_SIZE, "256")!!.toLong()
    }

    @JvmStatic
    fun getMaxBitrateWifi(): String {
        return generalPrefs.getString(MAX_BITRATE_WIFI, "0")!!
    }

    @JvmStatic
    fun getMaxBitrateMobile(): String {
        return generalPrefs.getString(MAX_BITRATE_MOBILE, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatWifi(): String {
        return generalPrefs.getString(AUDIO_TRANSCODE_FORMAT_WIFI, "raw")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatMobile(): String {
        return generalPrefs.getString(AUDIO_TRANSCODE_FORMAT_MOBILE, "raw")!!
    }

    @JvmStatic
    fun isWifiOnly(): Boolean {
        return generalPrefs.getBoolean(WIFI_ONLY, false)
    }

    @JvmStatic
    fun isDataSavingMode(): Boolean {
        return generalPrefs.getBoolean(DATA_SAVING_MODE, false)
    }

    @JvmStatic
    fun setDataSavingMode(isDataSavingModeEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(DATA_SAVING_MODE, isDataSavingModeEnabled)
        }
    }

    @JvmStatic
    fun isStarredArtistsSyncEnabled(): Boolean {
        return generalPrefs.getBoolean(SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredArtistsSyncEnabled(isStarredSyncEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(
                SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE, isStarredSyncEnabled
            )
        }
    }

    @JvmStatic
    fun isStarredAlbumsSyncEnabled(): Boolean {
        return generalPrefs.getBoolean(SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredAlbumsSyncEnabled(isStarredSyncEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(
                SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE, isStarredSyncEnabled
            )
        }
    }

    @JvmStatic
    fun isStarredSyncEnabled(): Boolean {
        return generalPrefs.getBoolean(SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredSyncEnabled(isStarredSyncEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(
                SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, isStarredSyncEnabled
            )
        }
    }

    @JvmStatic
    fun showShuffleInsteadOfHeart(): Boolean {
        return generalPrefs.getBoolean(MINI_SHUFFLE_BUTTON_VISIBILITY, false)
    }

    @JvmStatic
    fun setShuffleInsteadOfHeart(enabled: Boolean) {
        generalPrefs.edit { putBoolean(MINI_SHUFFLE_BUTTON_VISIBILITY, enabled) }
    }

    @JvmStatic
    fun showServerUnreachableDialog(): Boolean {
        return generalPrefs.getLong(
                SERVER_UNREACHABLE, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setServerUnreachableDatetime() {
        generalPrefs.edit { putLong(SERVER_UNREACHABLE, System.currentTimeMillis()) }
    }

    @JvmStatic
    fun isSyncronizationEnabled(): Boolean {
        return generalPrefs.getBoolean(QUEUE_SYNCING, false)
    }

    @JvmStatic
    fun getSyncCountdownTimer(): Int {
        return generalPrefs.getString(QUEUE_SYNCING_COUNTDOWN, "5")!!.toInt()
    }

    @JvmStatic
    fun isCornerRoundingEnabled(): Boolean {
        return generalPrefs.getBoolean(ROUNDED_CORNER, false)
    }

    @JvmStatic
    fun getRoundedCornerSize(): Int {
        return generalPrefs.getString(ROUNDED_CORNER_SIZE, "12")!!.toInt()
    }

    @JvmStatic
    fun isPodcastSectionVisible(): Boolean {
        return generalPrefs.getBoolean(PODCAST_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setPodcastSectionHidden() {
        generalPrefs.edit { putBoolean(PODCAST_SECTION_VISIBILITY, false) }
    }

    @JvmStatic
    fun isRadioSectionVisible(): Boolean {
        return generalPrefs.getBoolean(RADIO_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setRadioSectionHidden() {
        generalPrefs.edit().putBoolean(RADIO_SECTION_VISIBILITY, false).apply()
    }

    @JvmStatic
    fun isMusicDirectorySectionVisible(): Boolean {
        return generalPrefs.getBoolean(MUSIC_DIRECTORY_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun getReplayGainMode(): String? {
        return generalPrefs.getString(REPLAY_GAIN_MODE, "disabled")
    }

    @JvmStatic
    fun isServerPrioritized(): Boolean {
        return generalPrefs.getBoolean(AUDIO_TRANSCODE_PRIORITY, false)
    }

    @JvmStatic
    fun getStreamingCacheStoragePreference(): Int {
        return generalPrefs.getString(STREAMING_CACHE_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setStreamingCacheStoragePreference(streamingCachePreference: Int) {
        return generalPrefs.edit {
            putString(
                STREAMING_CACHE_STORAGE,
                streamingCachePreference.toString()
            )
        }
    }

    @JvmStatic
    fun getDownloadStoragePreference(): Int {
        return generalPrefs.getString(DOWNLOAD_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setDownloadStoragePreference(storagePreference: Int) {
        return generalPrefs.edit {
            putString(
                DOWNLOAD_STORAGE,
                storagePreference.toString()
            )
        }
    }

    @JvmStatic
    fun getDownloadDirectoryUri(): String? {
        return generalPrefs.getString(DOWNLOAD_DIRECTORY_URI, null)
    }

    @JvmStatic
    fun setDownloadDirectoryUri(uri: String?) {
        val current = generalPrefs.getString(DOWNLOAD_DIRECTORY_URI, null)
        if (current != uri) {
            ExternalDownloadMetadataStore.clear()
        }
        generalPrefs.edit { putString(DOWNLOAD_DIRECTORY_URI, uri) }
    }

    @JvmStatic
    fun getDefaultDownloadViewType(): String {
        return generalPrefs.getString(
                DEFAULT_DOWNLOAD_VIEW_TYPE,
                Constants.DOWNLOAD_TYPE_TRACK
        )!!
    }

    @JvmStatic
    fun setDefaultDownloadViewType(viewType: String) {
        return generalPrefs.edit {
            putString(
                DEFAULT_DOWNLOAD_VIEW_TYPE,
                viewType
            )
        }
    }

    @JvmStatic
    fun preferTranscodedDownload(): Boolean {
        return generalPrefs.getBoolean(AUDIO_TRANSCODE_DOWNLOAD, false)
    }

    @JvmStatic
    fun isServerPrioritizedInTranscodedDownload(): Boolean {
        return generalPrefs.getBoolean(AUDIO_TRANSCODE_DOWNLOAD_PRIORITY, false)
    }

    @JvmStatic
    fun getBitrateTranscodedDownload(): String {
        return generalPrefs.getString(MAX_BITRATE_DOWNLOAD, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatTranscodedDownload(): String {
        return generalPrefs.getString(AUDIO_TRANSCODE_FORMAT_DOWNLOAD, "raw")!!
    }

    @JvmStatic
    fun isSharingEnabled(): Boolean {
        return generalPrefs.getBoolean(SHARE, false)
    }

    @JvmStatic
    fun isScrobblingEnabled(): Boolean {
        return generalPrefs.getBoolean(SCROBBLING, true)
    }

    @JvmStatic
    fun askForEstimateContentLength(): Boolean {
        return generalPrefs.getBoolean(ESTIMATE_CONTENT_LENGTH, false)
    }

    @JvmStatic
    fun getBufferingStrategy(): Double {
        return generalPrefs.getString(BUFFERING_STRATEGY, "1")!!.toDouble()
    }

    @JvmStatic
    fun getMinStarRatingAccepted(): Int {
        return generalPrefs.getInt(MIN_STAR_RATING, 0)
    }

    @JvmStatic
    fun isDisplayAlwaysOn(): Boolean {
        return generalPrefs.getBoolean(ALWAYS_ON_DISPLAY, false)
    }

    @JvmStatic
    fun showAudioQuality(): Boolean {
        return generalPrefs.getBoolean(AUDIO_QUALITY_PER_ITEM, false)
    }

    @JvmStatic
    fun getHomeSectorList(): String? {
        return generalPrefs.getString(HOME_SECTOR_LIST, null)
    }

    @JvmStatic
    fun setHomeSectorList(extension: List<HomeSector>?) {
        generalPrefs.edit { putString(HOME_SECTOR_LIST, Gson().toJson(extension)) }
    }

    @JvmStatic
    fun showItemStarRating(): Boolean {
        return generalPrefs.getBoolean(SONG_RATING_PER_ITEM, false)
    }

    @JvmStatic
    fun showItemRating(): Boolean {
        return generalPrefs.getBoolean(RATING_PER_ITEM, false)
    }


    @JvmStatic
    fun isGithubUpdateEnabled(): Boolean {
        return generalPrefs.getBoolean(GITHUB_UPDATE_CHECK, true)
    }

    @JvmStatic
    fun showTempusUpdateDialog(): Boolean {
        return generalPrefs.getLong(
                NEXT_UPDATE_CHECK, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setTempusUpdateReminder() {
        generalPrefs.edit { putLong(NEXT_UPDATE_CHECK, System.currentTimeMillis()) }
    }

    @JvmStatic
    fun isContinuousPlayEnabled(): Boolean {
        return generalPrefs.getBoolean(CONTINUOUS_PLAY, true)
    }

    @JvmStatic
    fun setLastInstantMix() {
        generalPrefs.edit().putLong(LAST_INSTANT_MIX, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun isInstantMixUsable(): Boolean {
        return generalPrefs.getLong(
                LAST_INSTANT_MIX, 0
        ) + 5000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setAllowPlaylistDuplicates(allowDuplicates: Boolean) {
        return generalPrefs.edit {
            putString(
                ALLOW_PLAYLIST_DUPLICATES,
                allowDuplicates.toString()
            )
        }
    }

    @JvmStatic
    fun allowPlaylistDuplicates(): Boolean {
        return generalPrefs.getBoolean(ALLOW_PLAYLIST_DUPLICATES, false)
    }

    @JvmStatic
    fun setEqualizerEnabled(enabled: Boolean) {
        generalPrefs.edit { putBoolean(EQUALIZER_ENABLED, enabled) }
    }

    @JvmStatic
    fun isEqualizerEnabled(): Boolean {
        return generalPrefs.getBoolean(EQUALIZER_ENABLED, false)
    }

    @JvmStatic
    fun setEqualizerBandLevels(bandLevels: ShortArray) {
        val asString = bandLevels.joinToString(",")
        generalPrefs.edit { putString(EQUALIZER_BAND_LEVELS, asString) }
    }

    @JvmStatic
    fun getEqualizerBandLevels(bandCount: Short): ShortArray {
        val str = generalPrefs.getString(EQUALIZER_BAND_LEVELS, null)
        if (str.isNullOrBlank()) {
            return ShortArray(bandCount.toInt())
        }
        val parts = str.split(",")
        if (parts.size < bandCount) return ShortArray(bandCount.toInt())
        return ShortArray(bandCount.toInt()) { i -> parts[i].toShortOrNull() ?: 0 }
    }

    @JvmStatic
    fun showAlbumDetail(): Boolean {
        return generalPrefs.getBoolean(ALBUM_DETAIL, false)
    }

    @JvmStatic
    fun getAlbumSortOrder(): String {
        return generalPrefs.getString(ALBUM_SORT_ORDER, DEFAULT_ALBUM_SORT_ORDER) ?: DEFAULT_ALBUM_SORT_ORDER
    }

    @JvmStatic
    fun setAlbumSortOrder(sortOrder: String) {
        generalPrefs.edit { putString(ALBUM_SORT_ORDER, sortOrder) }
    }

    @JvmStatic
    fun getArtistSortOrder(): String {
        Log.d("Preferences", "getSortOrder")
        return if (generalPrefs.getBoolean(ARTIST_SORT_BY_ALBUM_COUNT, false))
            Constants.ARTIST_ORDER_BY_ALBUM_COUNT
        else
            Constants.ARTIST_ORDER_BY_NAME
    }

    @JvmStatic
    fun isSearchSortingChronologicallyEnabled(): Boolean {
        return generalPrefs.getBoolean(SORT_SEARCH_CHRONOLOGICALLY, false)
    }

    @JvmStatic
    fun getArtistDisplayBiography(): Boolean {
        return generalPrefs.getBoolean(ARTIST_DISPLAY_BIOGRAPHY, true)
    }

    @JvmStatic
    fun setArtistDisplayBiography(displayBiographyEnabled: Boolean) {
        generalPrefs.edit { putBoolean(ARTIST_DISPLAY_BIOGRAPHY, displayBiographyEnabled) }
    }

    @JvmStatic
    fun getTheme(defaultMode: String): String {
        return generalPrefs.getString(THEME, defaultMode) ?: defaultMode
    }

    @JvmStatic
    fun getString(key: String, default: String): String{
        return generalPrefs.getString(key, default)?: default
    }

    @JvmStatic
    fun putString(prefKey: String, value: String) {
        generalPrefs.edit { putString(prefKey, value) }
    }
}