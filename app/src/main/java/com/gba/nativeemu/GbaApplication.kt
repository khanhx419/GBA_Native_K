package com.gba.nativeemu

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class GbaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                Log.e("GbaNativeCrash", "UNCAUGHT CRASH on thread ${thread.name}:\n$stackTrace")

                val crashFile = File(filesDir, "crash.log")
                crashFile.writeText("CRASH on ${System.currentTimeMillis()}\nThread: ${thread.name}\n$stackTrace")
            } catch (e: Exception) {
                Log.e("GbaNativeCrash", "Error writing crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
