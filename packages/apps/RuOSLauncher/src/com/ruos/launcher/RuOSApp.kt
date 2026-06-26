package com.ruos.launcher

import android.app.Application
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RuOSApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mainHandler = Handler(Looper.getMainLooper())

    lateinit var appRepository: AppRepository
    lateinit var prewarmManager: PrewarmManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        appRepository = AppRepository(this)
        prewarmManager = PrewarmManager(this)
        prewarmManager.prewarmRussianApps()
    }

    companion object {
        lateinit var instance: RuOSApp
            private set
    }
}
