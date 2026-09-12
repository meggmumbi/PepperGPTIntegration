package com.example.peppergptintegration

import android.content.Context
import android.util.Log

/**
 * Runtime-editable backend address.
 *
 * `BuildConfig.BASE_URL` is baked in at compile time, which is fine for the
 * robot (it sits on one network) but not for phone testing: the laptop's LAN
 * address changes, and rebuilding the APK to chase it is not a workflow.
 *
 * The compiled value remains the default, so nothing changes unless someone
 * deliberately overrides it. The override is stored per device.
 */
object AppConfig {

    private const val PREFS = "app_config"
    private const val KEY_BASE_URL = "base_url"

    private var overridden: String? = null

    /**
     * Backend root, always with a single trailing slash.
     *
     * Call [init] from Application/Activity start-up. If it has not been
     * called, this falls back to the compiled-in value rather than throwing --
     * an unconfigured build should still behave exactly as it did before.
     */
    val baseUrl: String
        get() = overridden ?: BuildConfig.BASE_URL

    fun init(context: Context) {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
        overridden = stored?.takeIf { it.isNotBlank() }?.let { normalise(it) }
        Log.d("AppConfig", "backend: $baseUrl" +
            if (overridden != null) " (overridden)" else " (compiled default)")
    }

    fun setBaseUrl(context: Context, value: String) {
        val cleaned = normalise(value)
        overridden = cleaned.takeIf { it.isNotBlank() }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, cleaned)
            .apply()
        Log.d("AppConfig", "backend set to $baseUrl")
    }

    fun reset(context: Context) {
        overridden = null
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_BASE_URL).apply()
    }

    /**
     * Accept what someone will actually type -- "192.168.1.42:8000",
     * "http://192.168.1.42:8000", with or without a trailing slash -- and
     * return a form the existing string concatenation still works with.
     */
    private fun normalise(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        if (!value.endsWith("/")) value = "$value/"
        return value
    }
}
