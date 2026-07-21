package com.xiaozhi

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.UUID

object AppState {
    private const val PREFS_NAME = "xiaozhi_prefs"
    private const val SECURE_PREFS_NAME = "xiaozhi_secure_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_OTA_URL = "ota_url"
    private const val KEY_WSS_URL = "wss_url"
    private const val KEY_MUSIC_WS_URL = "music_ws_url"
    private const val KEY_AUTO_LISTENING = "auto_listening"
    private const val KEY_AVATAR_URI = "avatar_uri"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_AVATAR = "user_avatar"
    private const val KEY_MCP_MUSIC_ENABLED = "mcp_music_enabled"
    private const val KEY_MCP_VIDEO_ENABLED = "mcp_video_enabled"
    private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
    private const val KEY_PROFILE_AVATAR_URI = "profile_avatar_uri"
    private const val KEY_PROFILE_FULL_NAME = "profile_full_name"
    private const val KEY_PROFILE_ADDRESS = "profile_address"
    private const val KEY_PROFILE_PHONE = "profile_phone"
    private const val KEY_PROFILE_BIRTHDAY = "profile_birthday"
    private const val KEY_APP_FOREGROUND = "app_foreground"
    private const val KEY_DEVICE_NAME = "device_name"

    // Home Assistant Keys
    private const val KEY_HA_URL = "ha_url"
    private const val KEY_HA_TOKEN = "ha_token"
    private const val KEY_HA_ENABLED = "ha_enabled"

    // WebSocket Token (bổ sung)
    private const val KEY_WS_TOKEN = "ws_token"

    // ===== QUẢN LÝ WHITELIST THÔNG BÁO =====
    private const val KEY_NOTIFICATION_WHITELIST = "notification_whitelist"

    // ===== QUẢN LÝ BẬT/TẮT ĐỌC THÔNG BÁO TOÀN CỤC =====
    private const val KEY_NOTIFICATION_READING_ENABLED = "notification_reading_enabled"

    // Lấy SharedPreferences thường (cho dữ liệu không nhạy cảm)
    private fun getPrefs(ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Lấy EncryptedSharedPreferences cho dữ liệu nhạy cảm (token, URL)
    private fun getSecurePrefs(ctx: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ---------- Device & Client ----------
    fun getDeviceId(ctx: Context): String {
        val prefs = getPrefs(ctx)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = generateMacLikeId()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getClientId(ctx: Context): String {
        val prefs = getPrefs(ctx)
        var id = prefs.getString(KEY_CLIENT_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, id).apply()
        }
        return id
    }

    // ---------- Activation ----------
    fun isActivated(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_ACTIVATED, false)
    }

    fun setActivated(ctx: Context, activated: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_ACTIVATED, activated).apply()
    }

    // ---------- OTA (nhạy cảm) ----------
    fun getOtaUrl(ctx: Context, default: String = "https://api.tenclass.net/xiaozhi/ota/"): String {
        return getSecurePrefs(ctx).getString(KEY_OTA_URL, default) ?: default
    }

    fun setOtaUrl(ctx: Context, url: String) {
        getSecurePrefs(ctx).edit().putString(KEY_OTA_URL, url).apply()
    }

    // ---------- WebSocket (nhạy cảm) ----------
    fun getWssUrl(ctx: Context): String? {
        return getSecurePrefs(ctx).getString(KEY_WSS_URL, null)
    }

    fun setWssUrl(ctx: Context, url: String?) {
        getSecurePrefs(ctx).edit().putString(KEY_WSS_URL, url).apply()
    }

    // ---------- WebSocket Token (bổ sung) ----------
    fun getWsToken(ctx: Context): String? {
        return getSecurePrefs(ctx).getString(KEY_WS_TOKEN, null)
    }

    fun setWsToken(ctx: Context, token: String?) {
        getSecurePrefs(ctx).edit().putString(KEY_WS_TOKEN, token).apply()
    }

    // ---------- Music WebSocket (nhạy cảm) ----------
    fun getMusicWsUrl(ctx: Context, default: String = "ws://localhost:5001/ws"): String {
        return getSecurePrefs(ctx).getString(KEY_MUSIC_WS_URL, default) ?: default
    }

    fun setMusicWsUrl(ctx: Context, url: String) {
        getSecurePrefs(ctx).edit().putString(KEY_MUSIC_WS_URL, url).apply()
    }

    // ---------- Auto Listening ----------
    fun isAutoListeningEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_AUTO_LISTENING, true)
    }

    fun setAutoListeningEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_AUTO_LISTENING, enabled).apply()
    }

    // ---------- Avatar ----------
    fun getAvatarUri(ctx: Context): String? {
        return getPrefs(ctx).getString(KEY_AVATAR_URI, null)
    }

    fun setAvatarUri(ctx: Context, uri: String) {
        getPrefs(ctx).edit().putString(KEY_AVATAR_URI, uri).apply()
    }

    // ---------- Login ----------
    fun isLoggedIn(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setLoggedIn(ctx: Context, loggedIn: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply()
    }

    fun getUserName(ctx: Context): String? {
        return getPrefs(ctx).getString(KEY_USER_NAME, null)
    }

    fun setUserName(ctx: Context, name: String?) {
        getPrefs(ctx).edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserEmail(ctx: Context): String? {
        return getPrefs(ctx).getString(KEY_USER_EMAIL, null)
    }

    fun setUserEmail(ctx: Context, email: String?) {
        getPrefs(ctx).edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun getUserAvatar(ctx: Context): String? {
        return getPrefs(ctx).getString(KEY_USER_AVATAR, null)
    }

    fun setUserAvatar(ctx: Context, url: String?) {
        getPrefs(ctx).edit().putString(KEY_USER_AVATAR, url).apply()
    }

    // ---------- MCP ----------
    fun isMcpMusicEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_MCP_MUSIC_ENABLED, true)
    }

    fun setMcpMusicEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_MCP_MUSIC_ENABLED, enabled).apply()
    }

    fun isMcpVideoEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_MCP_VIDEO_ENABLED, true)
    }

    fun setMcpVideoEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_MCP_VIDEO_ENABLED, enabled).apply()
    }

    // ---------- Wake Word ----------
    fun isWakeWordEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_WAKE_WORD_ENABLED, false)
    }

    fun setWakeWordEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }

    // ---------- Mic Sensitivity ----------
    fun getMicSensitivity(ctx: Context): Int {
        return getPrefs(ctx).getInt(KEY_MIC_SENSITIVITY, 50)
    }

    fun setMicSensitivity(ctx: Context, value: Int) {
        getPrefs(ctx).edit().putInt(KEY_MIC_SENSITIVITY, value).apply()
    }

    // ---------- Profile Avatar (local) ----------
    fun getProfileAvatarUri(ctx: Context): String? {
        return getPrefs(ctx).getString(KEY_PROFILE_AVATAR_URI, null)
    }

    fun setProfileAvatarUri(ctx: Context, uri: String?) {
        getPrefs(ctx).edit().putString(KEY_PROFILE_AVATAR_URI, uri).apply()
    }

    fun getEffectiveAvatarUri(ctx: Context): String? {
        return getUserAvatar(ctx) ?: getProfileAvatarUri(ctx)
    }

    // ---------- Extended profile fields ----------
    fun getProfileField(ctx: Context, key: String): String? {
        return getPrefs(ctx).getString(key, null)
    }

    fun setProfileField(ctx: Context, key: String, value: String?) {
        getPrefs(ctx).edit().putString(key, value).apply()
    }

    // ---------- Foreground State ----------
    fun setAppForeground(ctx: Context, foreground: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_APP_FOREGROUND, foreground).apply()
    }

    fun isAppForeground(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_APP_FOREGROUND, false)
    }

    // ---------- Device Name ----------
    fun getDeviceName(ctx: Context): String {
        val prefs = getPrefs(ctx)
        var name = prefs.getString(KEY_DEVICE_NAME, null)
        if (name == null) {
            val deviceId = getDeviceId(ctx)
            val short = deviceId.replace(":", "").takeLast(4).uppercase()
            name = "Lily-$short"
            prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
        }
        return name
    }

    // ---------- Home Assistant Settings ----------
    fun getHaUrl(ctx: Context): String? = getSecurePrefs(ctx).getString(KEY_HA_URL, null)
    fun setHaUrl(ctx: Context, url: String?) {
        getSecurePrefs(ctx).edit().putString(KEY_HA_URL, url).apply()
    }
    fun getHaToken(ctx: Context): String? = getSecurePrefs(ctx).getString(KEY_HA_TOKEN, null)
    fun setHaToken(ctx: Context, token: String?) {
        getSecurePrefs(ctx).edit().putString(KEY_HA_TOKEN, token).apply()
    }
    fun isHaEnabled(ctx: Context): Boolean = getPrefs(ctx).getBoolean(KEY_HA_ENABLED, false)
    fun setHaEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_HA_ENABLED, enabled).apply()
    }

    // ---------- Notification Whitelist ----------
    fun getNotificationWhitelist(ctx: Context): Set<String> {
        return getPrefs(ctx).getStringSet(KEY_NOTIFICATION_WHITELIST, emptySet()) ?: emptySet()
    }

    fun setNotificationWhitelist(ctx: Context, whitelist: Set<String>) {
        getPrefs(ctx).edit().putStringSet(KEY_NOTIFICATION_WHITELIST, whitelist).apply()
    }

    fun isNotificationEnabledForApp(ctx: Context, packageName: String): Boolean {
        return getNotificationWhitelist(ctx).contains(packageName)
    }

    fun toggleNotificationForApp(ctx: Context, packageName: String, enabled: Boolean) {
        val current = getNotificationWhitelist(ctx).toMutableSet()
        if (enabled) current.add(packageName) else current.remove(packageName)
        setNotificationWhitelist(ctx, current)
    }

    // ---------- Global Notification Reading Toggle ----------
    fun isNotificationReadingEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_NOTIFICATION_READING_ENABLED, true)
    }

    fun setNotificationReadingEnabled(ctx: Context, enabled: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_NOTIFICATION_READING_ENABLED, enabled).apply()
    }

    // ---------- Helper ----------
    private fun generateMacLikeId(): String {
        val mac = ByteArray(6)
        java.util.Random().nextBytes(mac)
        return mac.joinToString(":") { String.format("%02x", it) }
    }
}