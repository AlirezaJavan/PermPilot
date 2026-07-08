package io.github.alirezajavan.permpilot

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings as KeyValueSettings

/**
 * Default [PermissionPersistence] implementation for Android using [SharedPreferences].
 */
class SharedPreferencesPermissionPersistence(
    context: Context,
    prefsName: String = "io.github.alirezajavan.permpilot.state",
) : PermissionPersistence {
    private val prefs: KeyValueSettings =
        SharedPreferencesSettings(
            context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE),
        )

    override fun isRequested(permission: Permission): Boolean = prefs.getBoolean(requestedKey(permission), false)

    override fun markRequested(permission: Permission) {
        prefs.putBoolean(requestedKey(permission), true)
    }

    private fun requestedKey(permission: Permission): String = "requested_${permission.persistenceKey()}"
}
