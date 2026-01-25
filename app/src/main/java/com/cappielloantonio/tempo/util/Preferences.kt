package com.cappielloantonio.tempo.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.model.HomeSector
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.gson.Gson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------- DataStore 扩展 ----------------------

val Context.secureDataStore by preferencesDataStore("secure_prefs")

// ---------------------- Tink 加密管理 ----------------------

object CryptoManager {

    init {
        // 注册 Tink AEAD 配置
        AeadConfig.register()
    }

    private val keysetHandle by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(
                App.getContext(),
                "tempo_master_keyset",
                "tempo_master_keyset_prefs"
            )
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://tempo_master_key")
            .build()
            .keysetHandle
    }

    private val aead: Aead by lazy {
        keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plainText: String): String {
        val bytes = plainText.toByteArray()
        val encrypted = aead.encrypt(bytes, null)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    fun decrypt(cipherTextBase64: String): String {
        val bytes = android.util.Base64.decode(cipherTextBase64, android.util.Base64.NO_WRAP)
        val decrypted = aead.decrypt(bytes, null)
        return String(decrypted)
    }
}

// ---------------------- 通用加密读写封装 ----------------------

private fun <T> encryptedFlow(
    keyName: String,
    decode: (String) -> T
): Flow<T?> {
    val key = stringPreferencesKey(keyName)
    val context = App.getContext()
    return context.secureDataStore.data.map { prefs ->
        prefs[key]?.let { enc ->
            val decrypted = CryptoManager.decrypt(enc)
            decode(decrypted)
        }
    }
}

private suspend fun <T> setEncrypted(
    keyName: String,
    value: T?,
    encode: (T) -> String
) {
    val key = stringPreferencesKey(keyName)
    val context = App.getContext()
    context.secureDataStore.edit { prefs ->
        if (value == null) {
            prefs.remove(key)
        } else {
            prefs[key] = CryptoManager.encrypt(encode(value))
        }
    }
}

// ---------------------- 安全偏好封装（敏感数据） ----------------------

object SecurePrefs {

    // password
    val passwordFlow: Flow<String?> =
        encryptedFlow(PrefKeys.PASSWORD) { it }

    suspend fun setPassword(value: String?) {
        setEncrypted(PrefKeys.PASSWORD, value) { it }
        Preferences.markLogged(value != null)
    }

    // token
    val tokenFlow: Flow<String?> =
        encryptedFlow(PrefKeys.TOKEN) { it }

    suspend fun setToken(value: String?) =
        setEncrypted(PrefKeys.TOKEN, value) { it }

    // salt
    val saltFlow: Flow<String?> =
        encryptedFlow(PrefKeys.SALT) { it }

    suspend fun setSalt(value: String?) =
        setEncrypted(PrefKeys.SALT, value) { it }

    // Navidrome token
    val navidromeTokenFlow: Flow<String?> =
        encryptedFlow(PrefKeys.NAVIDROME_TOKEN) { it }

    suspend fun setNavidromeToken(value: String?) =
        setEncrypted(PrefKeys.NAVIDROME_TOKEN, value) { it }
}

// ---------------------- Preferences 主体 ----------------------
private object PrefKeys {
    const val THEME = "theme"
    const val SERVER = "server"
    const val USER = "user"
    const val PASSWORD = "password"
    const val TOKEN = "token"
    const val NAVIDROME_TOKEN = "NAVIDROME_TOKEN"
    const val SALT = "salt"
    const val LOW_SECURITY = "low_security"
    const val BATTERY_OPTIMIZATION = "battery_optimization"
    const val SERVER_ID = "server_id"
    const val OPEN_SUBSONIC = "open_subsonic"
    const val OPEN_SUBSONIC_EXTENSIONS = "open_subsonic_extensions"
    const val LOCAL_ADDRESS = "local_address"
    const val IN_USE_SERVER_ADDRESS = "in_use_server_address"
    const val NEXT_SERVER_SWITCH = "next_server_switch"
    const val PLAYBACK_SPEED = "playback_speed"
    const val SKIP_SILENCE = "skip_silence"
    const val SHUFFLE_MODE = "shuffle_mode"
    const val REPEAT_MODE = "repeat_mode"
    const val IMAGE_CACHE_SIZE = "image_cache_size"
    const val STREAMING_CACHE_SIZE = "streaming_cache_size"
    const val IMAGE_SIZE = "image_size"
    const val MAX_BITRATE_WIFI = "max_bitrate_wifi"
    const val MAX_BITRATE_MOBILE = "max_bitrate_mobile"
    const val AUDIO_TRANSCODE_FORMAT_WIFI = "audio_transcode_format_wifi"
    const val AUDIO_TRANSCODE_FORMAT_MOBILE = "audio_transcode_format_mobile"
    const val WIFI_ONLY = "wifi_only"
    const val DATA_SAVING_MODE = "data_saving_mode"
    const val SERVER_UNREACHABLE = "server_unreachable"
    const val SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE = "sync_starred_artists_for_offline_use"
    const val SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE = "sync_starred_albums_for_offLINE_USE"
    const val SYNC_STARRED_TRACKS_FOR_OFFLINE_USE = "sync_starred_tracks_for_offline_use"
    const val QUEUE_SYNCING = "queue_syncing"
    const val QUEUE_SYNCING_COUNTDOWN = "queue_syncing_countdown"
    const val ROUNDED_CORNER = "rounded_corner"
    const val ROUNDED_CORNER_SIZE = "rounded_corner_size"
    const val PODCAST_SECTION_VISIBILITY = "podcast_section_visibility"
    const val RADIO_SECTION_VISIBILITY = "radio_section_visibility"
    const val AUTO_DOWNLOAD_LYRICS = "auto_download_lyrics"
    const val MUSIC_DIRECTORY_SECTION_VISIBILITY = "music_directory_section_visibility"
    const val REPLAY_GAIN_MODE = "replay_gain_mode"
    const val AUDIO_TRANSCODE_PRIORITY = "audio_transcode_priority"
    const val STREAMING_CACHE_STORAGE = "streaming_cache_storage"
    const val DOWNLOAD_STORAGE = "download_storage"
    const val DOWNLOAD_DIRECTORY_URI = "download_directory_uri"
    const val DEFAULT_DOWNLOAD_VIEW_TYPE = "default_download_view_type"
    const val AUDIO_TRANSCODE_DOWNLOAD = "audio_transcode_download"
    const val AUDIO_TRANSCODE_DOWNLOAD_PRIORITY = "audio_transcode_download_priority"
    const val MAX_BITRATE_DOWNLOAD = "max_bitrate_download"
    const val AUDIO_TRANSCODE_FORMAT_DOWNLOAD = "audio_transcode_format_download"
    const val SHARE = "share"
    const val SCROBBLING = "scrobbling"
    const val ESTIMATE_CONTENT_LENGTH = "estimate_content_length"
    const val BUFFERING_STRATEGY = "buffering_strategy"
    const val SKIP_MIN_STAR_RATING = "skip_min_star_rating"
    const val MIN_STAR_RATING = "min_star_rating"
    const val ALWAYS_ON_DISPLAY = "always_on_display"
    const val AUDIO_QUALITY_PER_ITEM = "audio_quality_per_item"
    const val HOME_SECTOR_LIST = "home_sector_list"
    const val SONG_RATING_PER_ITEM = "song_rating_per_item"
    const val RATING_PER_ITEM = "rating_per_item"
    const val NEXT_UPDATE_CHECK = "next_update_check"
    const val GITHUB_UPDATE_CHECK = "github_update_check"
    const val CONTINUOUS_PLAY = "continuous_play"
    const val LAST_INSTANT_MIX = "last_instant_mix"
    const val ALLOW_PLAYLIST_DUPLICATES = "allow_playlist_duplicates"
    const val HOME_SORT_PLAYLISTS = "home_sort_playlists"
    const val DEFAULT_HOME_SORT_PLAYLISTS_SORT_ORDER = Constants.PLAYLIST_ORDER_BY_RANDOM
    const val EQUALIZER_ENABLED = "equalizer_enabled"
    const val EQUALIZER_BAND_LEVELS = "equalizer_band_levels"
    const val MINI_SHUFFLE_BUTTON_VISIBILITY = "mini_shuffle_button_visibility"
    const val ALBUM_DETAIL = "album_detail"
    const val ALBUM_SORT_ORDER = "album_sort_order"
    const val DEFAULT_ALBUM_SORT_ORDER = Constants.ALBUM_ORDER_BY_NAME
    const val ARTIST_SORT_BY_ALBUM_COUNT= "artist_sort_by_album_count"
    const val SORT_SEARCH_CHRONOLOGICALLY= "sort_search_chronologically"
    const val ARTIST_DISPLAY_BIOGRAPHY= "artist_display_biography"
    const val CURRENT_PUASED_FLAG = "CURRENT_PUASED_FLAG"
    const val LOGIN_FLAG = "LOGIN_FLAG" //登录标志位，同步存取方便判断。在password或token设值时同步设置。
}

object Preferences {

    const val THEME_KEY = PrefKeys.THEME

    // 普通配置：存储 UI 状态、非敏感设置
    private val generalPrefs: SharedPreferences by lazy {
        App.getContext().getSharedPreferences("general_settings", Context.MODE_PRIVATE)
    }


    // ---------------------- 旧 EncryptedSharedPreferences → DataStore 迁移 ----------------------

    /**
     * 一次性迁移旧的 EncryptedSharedPreferences 中的敏感数据到 DataStore。
     * 建议在 App.onCreate() 中用协程调用：
     *
     *   lifecycleScope.launch {
     *       Preferences.migrateOldEncryptedPrefs(App.getContext())
     *   }
     */
    suspend fun migrateOldEncryptedPrefs(context: Context) = withContext(Dispatchers.IO) {
        // 旧的 EncryptedSharedPreferences（已弃用，仅用于读取旧数据）
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val oldEncryptedPrefs = EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val password = oldEncryptedPrefs.getString(PrefKeys.PASSWORD, null)
        val token = oldEncryptedPrefs.getString(PrefKeys.TOKEN, null)
        val salt = oldEncryptedPrefs.getString(PrefKeys.SALT, null)
        val nav = oldEncryptedPrefs.getString(PrefKeys.NAVIDROME_TOKEN, null)

        if (password == null && token == null && salt == null && nav == null) {
            return@withContext
        }

        SecurePrefs.setPassword(password)
        SecurePrefs.setToken(token)
        SecurePrefs.setSalt(salt)
        SecurePrefs.setNavidromeToken(nav)

        oldEncryptedPrefs.edit { clear() }
    }

    // ---------------------- 新的敏感数据 API（基于 DataStore） ----------------------
    val isLoggedInFlow: Flow<Boolean> =
        combine(
            SecurePrefs.passwordFlow,
            SecurePrefs.tokenFlow,
            SecurePrefs.saltFlow
        ) { pwd, token, salt ->
            pwd != null || (token != null && salt != null)
        }


    @JvmStatic
    fun passwordFlow(): Flow<String?> = SecurePrefs.passwordFlow

    fun getPassword(): String{
        return ""
    }

    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun setPassword(password: String?) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SecurePrefs.setPassword(password)
        }
    }

    @JvmStatic
    fun tokenFlow(): Flow<String?> = SecurePrefs.tokenFlow

    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun setToken(token: String, salt: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SecurePrefs.setToken(token)
            SecurePrefs.setSalt(salt)
        }
    }

    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun clearLogin(){
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SecurePrefs.setToken(null)
            SecurePrefs.setSalt(null)
            SecurePrefs.setPassword(null)
            SecurePrefs.setNavidromeToken(null)
        }
    }

    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun resetTokenSalt(){
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SecurePrefs.setToken(null)
            SecurePrefs.setSalt(null)
        }
    }

    @JvmStatic
    fun saltFlow(): Flow<String?> = SecurePrefs.saltFlow

    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun setSalt(salt: String?) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SecurePrefs.setSalt(salt)
        }
    }

    @JvmStatic
    fun navidromeTokenFlow(): Flow<String?> = SecurePrefs.navidromeTokenFlow

    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun setNavToken(token: String?) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SecurePrefs.setNavidromeToken(token)
        }
    }

    @JvmStatic
    fun markLogged(value: Boolean) {
//        登录状态标记，在给password或token设值时同步设置。
        generalPrefs.edit { putBoolean(PrefKeys.LOGIN_FLAG, value) }
    }

    @JvmStatic
    fun isLogged(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.LOGIN_FLAG, false)
    }


    // ---------------------- 以下为原有 generalPrefs 相关方法（基本保持不变） ----------------------

    @JvmStatic
    fun isPaused(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.CURRENT_PUASED_FLAG, true)
    }

    @JvmStatic
    fun setIsPaused(value: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.CURRENT_PUASED_FLAG, value) }
    }

    @JvmStatic
    fun getServer(): String? {
        return generalPrefs.getString(PrefKeys.SERVER, null)
    }

    @JvmStatic
    fun setServer(server: String?) {
        generalPrefs.edit { putString(PrefKeys.SERVER, server) }
    }

    @JvmStatic
    fun getUser(): String? {
        return generalPrefs.getString(PrefKeys.USER, null)
    }

    @JvmStatic
    fun setUser(user: String?) {
        generalPrefs.edit { putString(PrefKeys.USER, user) }
    }

    @JvmStatic
    fun isLowScurity(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.LOW_SECURITY, false)
    }

    @JvmStatic
    fun setLowSecurity(isLowSecurity: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.LOW_SECURITY, isLowSecurity) }
    }

    @JvmStatic
    fun getServerId(): String? {
        return generalPrefs.getString(PrefKeys.SERVER_ID, null)
    }

    @JvmStatic
    fun setServerId(serverId: String?) {
        generalPrefs.edit { putString(PrefKeys.SERVER_ID, serverId) }
    }

    @JvmStatic
    fun isOpenSubsonic(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.OPEN_SUBSONIC, false)
    }

    @JvmStatic
    fun setOpenSubsonic(isOpenSubsonic: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.OPEN_SUBSONIC, isOpenSubsonic) }
    }

    @JvmStatic
    fun getOpenSubsonicExtensions(): String? {
        return generalPrefs.getString(PrefKeys.OPEN_SUBSONIC_EXTENSIONS, null)
    }

    @JvmStatic
    fun setOpenSubsonicExtensions(extension: List<OpenSubsonicExtension>) {
        generalPrefs.edit { putString(PrefKeys.OPEN_SUBSONIC_EXTENSIONS, Gson().toJson(extension)) }
    }

    @JvmStatic
    fun isAutoDownloadLyricsEnabled(): Boolean {
        val preferences = generalPrefs
        if (preferences.contains(PrefKeys.AUTO_DOWNLOAD_LYRICS)) {
            return preferences.getBoolean(PrefKeys.AUTO_DOWNLOAD_LYRICS, false)
        }
        return false
    }

    @JvmStatic
    fun setAutoDownloadLyricsEnabled(isEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(PrefKeys.AUTO_DOWNLOAD_LYRICS, isEnabled)
        }
    }

    @JvmStatic
    fun getLocalAddress(): String? {
        return generalPrefs.getString(PrefKeys.LOCAL_ADDRESS, null)
    }

    @JvmStatic
    fun setLocalAddress(address: String?) {
        generalPrefs.edit { putString(PrefKeys.LOCAL_ADDRESS, address) }
    }

    @JvmStatic
    fun getInUseServerAddress(): String? {
        return generalPrefs.getString(PrefKeys.IN_USE_SERVER_ADDRESS, null)
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
        generalPrefs.edit { putString(PrefKeys.IN_USE_SERVER_ADDRESS, inUseAddress) }
    }

    @JvmStatic
    fun isServerSwitchable(): Boolean {
        return generalPrefs.getLong(
            PrefKeys.NEXT_SERVER_SWITCH, 0
        ) + 15000 < System.currentTimeMillis() && !getServer().isNullOrEmpty() && !getLocalAddress().isNullOrEmpty()
    }

    @JvmStatic
    fun setServerSwitchableTimer() {
        generalPrefs.edit { putLong(PrefKeys.NEXT_SERVER_SWITCH, System.currentTimeMillis()) }
    }

    @JvmStatic
    fun askForOptimization(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.BATTERY_OPTIMIZATION, true)
    }

    @JvmStatic
    fun dontAskForOptimization() {
        generalPrefs.edit { putBoolean(PrefKeys.BATTERY_OPTIMIZATION, false) }
    }

    @JvmStatic
    fun getPlaybackSpeed(): Float {
        return generalPrefs.getFloat(PrefKeys.PLAYBACK_SPEED, 1f)
    }

    @JvmStatic
    fun setPlaybackSpeed(playbackSpeed: Float) {
        generalPrefs.edit { putFloat(PrefKeys.PLAYBACK_SPEED, playbackSpeed) }
    }

    fun resetPlaybackSpeed() {
        setPlaybackSpeed(1f)
    }

    @JvmStatic
    fun isSkipSilenceMode(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SKIP_SILENCE, false)
    }

    @JvmStatic
    fun setSkipSilenceMode(isSkipSilenceMode: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.SKIP_SILENCE, isSkipSilenceMode) }
    }

    @JvmStatic
    fun isShuffleModeEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SHUFFLE_MODE, false)
    }

    @JvmStatic
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.SHUFFLE_MODE, shuffleModeEnabled) }
    }

    @JvmStatic
    fun getRepeatMode(): Int {
        return generalPrefs.getInt(PrefKeys.REPEAT_MODE, Player.REPEAT_MODE_OFF)
    }

    @JvmStatic
    fun setRepeatMode(repeatMode: Int) {
        generalPrefs.edit { putInt(PrefKeys.REPEAT_MODE, repeatMode) }
    }

    @JvmStatic
    fun getImageCacheSize(): Int {
        return generalPrefs.getString(PrefKeys.IMAGE_CACHE_SIZE, "500")!!.toInt()
    }

    @JvmStatic
    fun getImageSize(): Int {
        return generalPrefs.getString(PrefKeys.IMAGE_SIZE, "-1")!!.toInt()
    }

    @JvmStatic
    fun getStreamingCacheSize(): Long {
        return generalPrefs.getString(PrefKeys.STREAMING_CACHE_SIZE, "256")!!.toLong()
    }

    @JvmStatic
    fun getMaxBitrateWifi(): String {
        return generalPrefs.getString(PrefKeys.MAX_BITRATE_WIFI, "0")!!
    }

    @JvmStatic
    fun getMaxBitrateMobile(): String {
        return generalPrefs.getString(PrefKeys.MAX_BITRATE_MOBILE, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatWifi(): String {
        return generalPrefs.getString(PrefKeys.AUDIO_TRANSCODE_FORMAT_WIFI, "raw")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatMobile(): String {
        return generalPrefs.getString(PrefKeys.AUDIO_TRANSCODE_FORMAT_MOBILE, "raw")!!
    }

    @JvmStatic
    fun isWifiOnly(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.WIFI_ONLY, false)
    }

    @JvmStatic
    fun isDataSavingMode(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.DATA_SAVING_MODE, false)
    }

    @JvmStatic
    fun setDataSavingMode(isDataSavingModeEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(PrefKeys.DATA_SAVING_MODE, isDataSavingModeEnabled)
        }
    }

    @JvmStatic
    fun isStarredArtistsSyncEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredArtistsSyncEnabled(isStarredSyncEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(
                PrefKeys.SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE, isStarredSyncEnabled
            )
        }
    }

    @JvmStatic
    fun isStarredAlbumsSyncEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredAlbumsSyncEnabled(isStarredSyncEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(
                PrefKeys.SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE, isStarredSyncEnabled
            )
        }
    }

    @JvmStatic
    fun isStarredSyncEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredSyncEnabled(isStarredSyncEnabled: Boolean) {
        generalPrefs.edit {
            putBoolean(
                PrefKeys.SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, isStarredSyncEnabled
            )
        }
    }

    @JvmStatic
    fun showShuffleInsteadOfHeart(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.MINI_SHUFFLE_BUTTON_VISIBILITY, false)
    }

    @JvmStatic
    fun setShuffleInsteadOfHeart(enabled: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.MINI_SHUFFLE_BUTTON_VISIBILITY, enabled) }
    }

    @JvmStatic
    fun showServerUnreachableDialog(): Boolean {
        return generalPrefs.getLong(
            PrefKeys.SERVER_UNREACHABLE, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setServerUnreachableDatetime() {
        generalPrefs.edit { putLong(PrefKeys.SERVER_UNREACHABLE, System.currentTimeMillis()) }
    }

    @JvmStatic
    fun isSyncronizationEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.QUEUE_SYNCING, false)
    }

    @JvmStatic
    fun getSyncCountdownTimer(): Int {
        return generalPrefs.getString(PrefKeys.QUEUE_SYNCING_COUNTDOWN, "5")!!.toInt()
    }

    @JvmStatic
    fun isCornerRoundingEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.ROUNDED_CORNER, false)
    }

    @JvmStatic
    fun getRoundedCornerSize(): Int {
        return generalPrefs.getString(PrefKeys.ROUNDED_CORNER_SIZE, "12")!!.toInt()
    }

    @JvmStatic
    fun isPodcastSectionVisible(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.PODCAST_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setPodcastSectionHidden() {
        generalPrefs.edit { putBoolean(PrefKeys.PODCAST_SECTION_VISIBILITY, false) }
    }

    @JvmStatic
    fun isRadioSectionVisible(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.RADIO_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setRadioSectionHidden() {
        generalPrefs.edit { putBoolean(PrefKeys.RADIO_SECTION_VISIBILITY, false) }
    }

    @JvmStatic
    fun isMusicDirectorySectionVisible(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.MUSIC_DIRECTORY_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun getReplayGainMode(): String? {
        return generalPrefs.getString(PrefKeys.REPLAY_GAIN_MODE, "disabled")
    }

    @JvmStatic
    fun isServerPrioritized(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.AUDIO_TRANSCODE_PRIORITY, false)
    }

    @JvmStatic
    fun getStreamingCacheStoragePreference(): Int {
        return generalPrefs.getString(PrefKeys.STREAMING_CACHE_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setStreamingCacheStoragePreference(streamingCachePreference: Int) {
        return generalPrefs.edit {
            putString(
                PrefKeys.STREAMING_CACHE_STORAGE,
                streamingCachePreference.toString()
            )
        }
    }

    @JvmStatic
    fun getDownloadStoragePreference(): Int {
        return generalPrefs.getString(PrefKeys.DOWNLOAD_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setDownloadStoragePreference(storagePreference: Int) {
        return generalPrefs.edit {
            putString(
                PrefKeys.DOWNLOAD_STORAGE,
                storagePreference.toString()
            )
        }
    }

    @JvmStatic
    fun getDownloadDirectoryUri(): String? {
        return generalPrefs.getString(PrefKeys.DOWNLOAD_DIRECTORY_URI, null)
    }

    @JvmStatic
    fun setDownloadDirectoryUri(uri: String?) {
        val current = generalPrefs.getString(PrefKeys.DOWNLOAD_DIRECTORY_URI, null)
        if (current != uri) {
            ExternalDownloadMetadataStore.clear()
        }
        generalPrefs.edit { putString(PrefKeys.DOWNLOAD_DIRECTORY_URI, uri) }
    }

    @JvmStatic
    fun getDefaultDownloadViewType(): String {
        return generalPrefs.getString(
            PrefKeys.DEFAULT_DOWNLOAD_VIEW_TYPE,
            Constants.DOWNLOAD_TYPE_TRACK
        )!!
    }

    @JvmStatic
    fun setDefaultDownloadViewType(viewType: String) {
        return generalPrefs.edit {
            putString(
                PrefKeys.DEFAULT_DOWNLOAD_VIEW_TYPE,
                viewType
            )
        }
    }

    @JvmStatic
    fun preferTranscodedDownload(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.AUDIO_TRANSCODE_DOWNLOAD, false)
    }

    @JvmStatic
    fun isServerPrioritizedInTranscodedDownload(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.AUDIO_TRANSCODE_DOWNLOAD_PRIORITY, false)
    }

    @JvmStatic
    fun getBitrateTranscodedDownload(): String {
        return generalPrefs.getString(PrefKeys.MAX_BITRATE_DOWNLOAD, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatTranscodedDownload(): String {
        return generalPrefs.getString(PrefKeys.AUDIO_TRANSCODE_FORMAT_DOWNLOAD, "raw")!!
    }

    @JvmStatic
    fun isSharingEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SHARE, false)
    }

    @JvmStatic
    fun isScrobblingEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SCROBBLING, true)
    }

    @JvmStatic
    fun askForEstimateContentLength(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.ESTIMATE_CONTENT_LENGTH, false)
    }

    @JvmStatic
    fun getBufferingStrategy(): Double {
        return generalPrefs.getString(PrefKeys.BUFFERING_STRATEGY, "1")!!.toDouble()
    }

    @JvmStatic
    fun getMinStarRatingAccepted(): Int {
        return generalPrefs.getInt(PrefKeys.MIN_STAR_RATING, 0)
    }

    @JvmStatic
    fun isDisplayAlwaysOn(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.ALWAYS_ON_DISPLAY, false)
    }

    @JvmStatic
    fun showAudioQuality(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.AUDIO_QUALITY_PER_ITEM, false)
    }

    @JvmStatic
    fun getHomeSectorList(): String? {
        return generalPrefs.getString(PrefKeys.HOME_SECTOR_LIST, null)
    }

    @JvmStatic
    fun setHomeSectorList(extension: List<HomeSector>?) {
        generalPrefs.edit { putString(PrefKeys.HOME_SECTOR_LIST, Gson().toJson(extension)) }
    }

    @JvmStatic
    fun showItemStarRating(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SONG_RATING_PER_ITEM, false)
    }

    @JvmStatic
    fun showItemRating(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.RATING_PER_ITEM, false)
    }

    @JvmStatic
    fun isGithubUpdateEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.GITHUB_UPDATE_CHECK, true)
    }

    @JvmStatic
    fun showTempusUpdateDialog(): Boolean {
        return generalPrefs.getLong(
            PrefKeys.NEXT_UPDATE_CHECK, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setTempusUpdateReminder() {
        generalPrefs.edit { putLong(PrefKeys.NEXT_UPDATE_CHECK, System.currentTimeMillis()) }
    }

    @JvmStatic
    fun isContinuousPlayEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.CONTINUOUS_PLAY, true)
    }

    @JvmStatic
    fun setLastInstantMix() {
        generalPrefs.edit { putLong(PrefKeys.LAST_INSTANT_MIX, System.currentTimeMillis()) }
    }

    @JvmStatic
    fun isInstantMixUsable(): Boolean {
        return generalPrefs.getLong(
            PrefKeys.LAST_INSTANT_MIX, 0
        ) + 5000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setAllowPlaylistDuplicates(allowDuplicates: Boolean) {
        return generalPrefs.edit {
            putString(
                PrefKeys.ALLOW_PLAYLIST_DUPLICATES,
                allowDuplicates.toString()
            )
        }
    }

    @JvmStatic
    fun allowPlaylistDuplicates(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.ALLOW_PLAYLIST_DUPLICATES, false)
    }

    @JvmStatic
    fun getHomeSortPlaylists(): String {
        return generalPrefs.getString(PrefKeys.HOME_SORT_PLAYLISTS, PrefKeys.DEFAULT_HOME_SORT_PLAYLISTS_SORT_ORDER)
            ?: PrefKeys.DEFAULT_HOME_SORT_PLAYLISTS_SORT_ORDER
    }

    @JvmStatic
    fun getHomeSortPlaylists(sortOrder: String) {
        generalPrefs.edit { putString(PrefKeys.HOME_SORT_PLAYLISTS, sortOrder) }
    }

    @JvmStatic
    fun setEqualizerEnabled(enabled: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.EQUALIZER_ENABLED, enabled) }
    }

    @JvmStatic
    fun isEqualizerEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.EQUALIZER_ENABLED, false)
    }

    @JvmStatic
    fun setEqualizerBandLevels(bandLevels: ShortArray) {
        val asString = bandLevels.joinToString(",")
        generalPrefs.edit { putString(PrefKeys.EQUALIZER_BAND_LEVELS, asString) }
    }

    @JvmStatic
    fun getEqualizerBandLevels(bandCount: Short): ShortArray {
        val str = generalPrefs.getString(PrefKeys.EQUALIZER_BAND_LEVELS, null)
        if (str.isNullOrBlank()) {
            return ShortArray(bandCount.toInt())
        }
        val parts = str.split(",")
        if (parts.size < bandCount) return ShortArray(bandCount.toInt())
        return ShortArray(bandCount.toInt()) { i -> parts[i].toShortOrNull() ?: 0 }
    }

    @JvmStatic
    fun showAlbumDetail(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.ALBUM_DETAIL, false)
    }

    @JvmStatic
    fun getAlbumSortOrder(): String {
        return generalPrefs.getString(PrefKeys.ALBUM_SORT_ORDER, PrefKeys.DEFAULT_ALBUM_SORT_ORDER)
            ?: PrefKeys.DEFAULT_ALBUM_SORT_ORDER
    }

    @JvmStatic
    fun setAlbumSortOrder(sortOrder: String) {
        generalPrefs.edit { putString(PrefKeys.ALBUM_SORT_ORDER, sortOrder) }
    }

    @JvmStatic
    fun getArtistSortOrder(): String {
        Log.d("Preferences", "getSortOrder")
        return if (generalPrefs.getBoolean(PrefKeys.ARTIST_SORT_BY_ALBUM_COUNT, false))
            Constants.ARTIST_ORDER_BY_ALBUM_COUNT
        else
            Constants.ARTIST_ORDER_BY_NAME
    }

    @JvmStatic
    fun isSearchSortingChronologicallyEnabled(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.SORT_SEARCH_CHRONOLOGICALLY, false)
    }

    @JvmStatic
    fun getArtistDisplayBiography(): Boolean {
        return generalPrefs.getBoolean(PrefKeys.ARTIST_DISPLAY_BIOGRAPHY, true)
    }

    @JvmStatic
    fun setArtistDisplayBiography(displayBiographyEnabled: Boolean) {
        generalPrefs.edit { putBoolean(PrefKeys.ARTIST_DISPLAY_BIOGRAPHY, displayBiographyEnabled) }
    }

    @JvmStatic
    fun getTheme(defaultMode: String): String {
        return generalPrefs.getString(PrefKeys.THEME, defaultMode) ?: defaultMode
    }



    @JvmStatic
    fun getString(key: String, default: String): String {
        return generalPrefs.getString(key, default) ?: default
    }

    @JvmStatic
    fun putString(prefKey: String, value: String) {
        generalPrefs.edit { putString(prefKey, value) }
    }
}
