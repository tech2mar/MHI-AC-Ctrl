package de.tech2mar.openwebui

import android.app.Application

class OpenWebUiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
