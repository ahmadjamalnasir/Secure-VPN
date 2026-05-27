package com.example

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("GlobalExceptionHandler", "Uncaught exception on thread ${thread.name}", exception)
            
            // Optionally, we could save the crash log to preferences or file system here
            // to show it on the next launch.
            
            // Re-throw to default handler so the system still knows it crashed
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
}
