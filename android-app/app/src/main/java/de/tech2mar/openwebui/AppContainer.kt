package de.tech2mar.openwebui

import android.content.Context
import de.tech2mar.openwebui.data.OpenWebUiApi
import de.tech2mar.openwebui.data.SettingsRepository

/**
 * Small hand-rolled dependency container (no DI framework needed for this app's size).
 */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context.applicationContext)
    val api = OpenWebUiApi()
}
