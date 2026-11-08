package org.samillimed.app.data.facility

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Which Firebase project this phone talks to. With nothing saved, it is the app's built-in project
 * (the owner's own). A facility on its own project is connected once with a setup code written
 * as projectId|appId|apiKey. These values are not secrets: the database rules protect the data.
 */
object FacilityConnection {
    private const val PREFS = "facility_connection"
    private const val APP_NAME = "facility"

    class Settings(val projectId: String, val appId: String, val apiKey: String)

    private val projectPattern = Regex("^[a-z][a-z0-9-]{3,}$")
    private val appPattern = Regex("^\\d+:\\d+:android:[0-9a-fA-F]+$")

    @Volatile
    private var custom = false

    /** Reads "projectId|appId|apiKey". Returns null when the text is not a valid setup code. */
    fun parse(code: String): Settings? {
        val parts = code.trim().split("|").map { it.trim() }
        if (parts.size != 3) return null
        val (project, appId, key) = parts
        if (!projectPattern.matches(project) || !appPattern.matches(appId)) return null
        if (!key.startsWith("AIza") || key.length < 30) return null
        return Settings(project, appId, key)
    }

    fun saved(context: Context): Settings? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val project = p.getString("projectId", null) ?: return null
        val appId = p.getString("appId", null) ?: return null
        val key = p.getString("apiKey", null) ?: return null
        return Settings(project, appId, key)
    }

    fun save(context: Context, settings: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("projectId", settings.projectId)
            .putString("appId", settings.appId)
            .putString("apiKey", settings.apiKey)
            .commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Call once, first thing when the app starts, before anything touches Firebase. */
    fun init(context: Context) {
        val settings = saved(context) ?: return
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).none { it.name == APP_NAME }) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setProjectId(settings.projectId)
                    .setApplicationId(settings.appId)
                    .setApiKey(settings.apiKey)
                    .build(),
                APP_NAME,
            )
        }
        custom = true
    }

    /** The facility's own project when one is saved, otherwise the built-in one. */
    fun app(): FirebaseApp =
        if (custom) FirebaseApp.getInstance(APP_NAME) else FirebaseApp.getInstance()
}
