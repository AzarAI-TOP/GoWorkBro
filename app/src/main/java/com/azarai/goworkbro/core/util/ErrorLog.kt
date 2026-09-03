package com.azarai.goworkbro.core.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Rolling error log at <filesDir>/logs/error.log (512 KB cap), plus logcat. */
object ErrorLog {
    private const val MAX_BYTES = 512 * 1024

    fun install(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, "error.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(file, thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun log(context: Context, tag: String, throwable: Throwable) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        append(File(dir, "error.log"), tag, throwable)
    }

    private fun append(file: File, tag: String, throwable: Throwable) {
        if (file.exists() && file.length() > MAX_BYTES) {
            file.writeText("")
        }
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        file.appendText("\n[${Dates.nowIso()}] [$tag]\n$sw\n")
    }
}
