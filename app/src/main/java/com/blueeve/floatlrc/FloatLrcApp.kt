package com.blueeve.floatlrc

import android.app.Application
import com.blueeve.floatlrc.data.AppPreferences

class FloatLrcApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
    }

    companion object {
        lateinit var instance: FloatLrcApp
            private set
    }
}
