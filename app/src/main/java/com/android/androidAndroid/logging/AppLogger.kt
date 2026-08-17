package com.android.androidAndroid.logging

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 同时输出到 Logcat 和应用私有文件的日志入口.
 *
 * 文件写入固定在单线程执行器中,避免阻塞调用线程和产生交叉日志.
 */
object AppLogger {

    private const val ACTIVE_FILE_NAME = "app.log"
    private const val MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L
    private const val MAX_FILE_COUNT = 5

    private val writerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "app-file-logger").apply { isDaemon = true }
    }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logDirectory: File? = null
    private var writer: BufferedWriter? = null

    @Synchronized
    fun init(context: Context) {
        if (logDirectory != null) return
        logDirectory = File(context.applicationContext.filesDir, "logs").also { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e("AppLogger", "无法创建应用日志目录: ${directory.absolutePath}")
                logDirectory = null
                return
            }
        }
        i("AppLogger", "文件日志已初始化")
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) =
        log(Log.VERBOSE, tag, message, throwable)

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        log(Log.DEBUG, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        log(Log.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Log.ERROR, tag, message, throwable)

    fun getLogDirectory(): File? = logDirectory

    /** 等待此前提交的日志落盘,主要用于导出或问题复现结束时调用. */
    fun flush(timeoutMs: Long = 2_000L): Boolean {
        if (logDirectory == null) return true
        val latch = CountDownLatch(1)
        writerExecutor.execute {
            try {
                writer?.flush()
            } finally {
                latch.countDown()
            }
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        Log.println(priority, tag, appendThrowable(message, throwable))
        if (logDirectory == null) return

        val threadName = Thread.currentThread().name
        writerExecutor.execute {
            runCatching {
                val line = buildLine(priority, tag, threadName, message, throwable)
                ensureWritableFile(line.toByteArray(StandardCharsets.UTF_8).size + 1)
                writer?.apply {
                    write(line)
                    newLine()
                    flush()
                }
            }.onFailure { error ->
                Log.e("AppLogger", "写入应用日志失败", error)
                closeWriter()
            }
        }
    }

    private fun buildLine(
        priority: Int,
        tag: String,
        threadName: String,
        message: String,
        throwable: Throwable?
    ): String {
        val timestamp = timestampFormat.format(Date())
        return "$timestamp ${priorityName(priority)} [$threadName] [$tag] ${appendThrowable(message, throwable)}"
    }

    private fun appendThrowable(message: String, throwable: Throwable?): String {
        if (throwable == null) return message
        return "$message\n${Log.getStackTraceString(throwable)}"
    }

    private fun ensureWritableFile(nextEntryBytes: Int) {
        val directory = logDirectory ?: return
        val activeFile = File(directory, ACTIVE_FILE_NAME)
        if (activeFile.exists() && activeFile.length() + nextEntryBytes > MAX_FILE_SIZE_BYTES) {
            closeWriter()
            rotateFiles(directory)
        }
        if (writer == null) {
            writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(activeFile, true), StandardCharsets.UTF_8)
            )
        }
    }

    private fun rotateFiles(directory: File) {
        File(directory, "$ACTIVE_FILE_NAME.${MAX_FILE_COUNT - 1}").delete()
        for (index in MAX_FILE_COUNT - 2 downTo 1) {
            val source = File(directory, "$ACTIVE_FILE_NAME.$index")
            if (source.exists()) {
                source.renameTo(File(directory, "$ACTIVE_FILE_NAME.${index + 1}"))
            }
        }
        val activeFile = File(directory, ACTIVE_FILE_NAME)
        if (activeFile.exists()) {
            activeFile.renameTo(File(directory, "$ACTIVE_FILE_NAME.1"))
        }
    }

    private fun closeWriter() {
        runCatching { writer?.close() }
        writer = null
    }

    private fun priorityName(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> priority.toString()
    }
}
