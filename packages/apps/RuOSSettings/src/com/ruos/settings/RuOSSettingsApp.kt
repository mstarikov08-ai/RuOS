package com.ruos.settings

import android.app.Application

class RuOSSettingsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: RuOSSettingsApp
            private set
    }
}
