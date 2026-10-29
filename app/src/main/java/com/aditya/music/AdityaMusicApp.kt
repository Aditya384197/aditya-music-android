package com.aditya.music

import android.app.Application
import com.aditya.music.data.database.MusicDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records uncaught Java/Kotlin crashes so they can be inspected after reopening the app.
 * This diagnostic does not prevent Android/native process crashes.
 */
class AdityaMusicApp : Application() {
    val database: MusicDatabase by lazy { MusicDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                File(filesDir, CRASH_REPORT_FILE).writeText(
                    "Aditya Music crash report\nThread: ${thread.name}\nTime: ${System.currentTimeMillis()}\n\n$stack"
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_REPORT_FILE = "last_crash_report.txt"
        lateinit var instance: AdityaMusicApp
            private set
    }
}
