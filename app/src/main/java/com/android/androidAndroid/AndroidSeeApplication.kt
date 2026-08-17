package com.android.androidAndroid

import android.app.Application
import com.android.androidAndroid.logging.AppLogger

class androidAndroidApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.init(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        if (instance === this) {
            instance = null
        }
    }

    companion object {
        @Volatile
        var pageWidth: Int = 1080

        @Volatile
        var pageHeight: Int = 1920

        @Volatile
        var instance: androidAndroidApplication? = null
            private set
    }
}
