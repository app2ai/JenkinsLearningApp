package com.example.jenkinslearning.app

import android.app.Application
import com.example.jenkinslearning.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class JenkinsLearningApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            // Koin logs definition resolution, not values, so this is safe in debug.
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@JenkinsLearningApplication)
            modules(appModules)
        }
    }
}
