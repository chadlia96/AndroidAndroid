package com.android.androidAndroid.junk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import com.android.androidAndroid.logging.AppLogger as Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object SystemTimeUtil {

    private const val TAG = "SystemTimeUtil"
    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_TIMEOUT_MILLIS = 5_000
    private const val NTP_EPOCH_OFFSET_SECONDS = 2_208_988_800L
    private val NTP_SERVERS = listOf(
        "ntp.aliyun.com",
        "time1.cloud.tencent.com",
        "time.cloudflare.com"
    )

    sealed class CorrectionResult {
        data class Success(val timeText: String) : CorrectionResult()
        data class RequiresSystemPermission(val timeText: String) : CorrectionResult()
        data class RequiresWriteSettingsPermission(val timeText: String) : CorrectionResult()
        data class Failed(val message: String) : CorrectionResult()
    }

    fun correctSystemTime(context: Context): CorrectionResult {
        val networkTime = fetchNetworkTimeMillis()
            ?: return CorrectionResult.Failed("无法获取网络时间")
        val timeText = formatBeijingTime(networkTime)

        if (!hasSetTimePermission(context)) {
            return CorrectionResult.RequiresSystemPermission(timeText)
        }

        return try {
            if (!SystemClock.setCurrentTimeMillis(networkTime)) {
                CorrectionResult.Failed("系统拒绝设置时间")
            } else {
                if (set24HourFormat(context)) {
                    CorrectionResult.Success(timeText)
                } else {
                    CorrectionResult.RequiresWriteSettingsPermission(timeText)
                }
            }
        } catch (_: SecurityException) {
            CorrectionResult.RequiresSystemPermission(timeText)
        }
    }

    fun fetchNetworkTimeMillis(): Long? {
        NTP_SERVERS.forEach { server ->
            val result = runCatching { fetchNtpTimeMillis(server) }
            result.getOrNull()?.let { return it }
            Log.w(TAG, "从NTP服务器获取时间失败: $server", result.exceptionOrNull())
        }
        return null
    }

    private fun fetchNtpTimeMillis(server: String): Long {
        val request = ByteArray(NTP_PACKET_SIZE)
        request[0] = 0x23
        val requestTime = System.currentTimeMillis()
        val requestElapsedRealtime = SystemClock.elapsedRealtime()
        writeTimestamp(request, 40, requestTime)

        DatagramSocket().use { socket ->
            socket.soTimeout = NTP_TIMEOUT_MILLIS
            val address = InetAddress.getByName(server)
            socket.send(DatagramPacket(request, request.size, address, NTP_PORT))

            val response = DatagramPacket(request, request.size)
            socket.receive(response)
            val responseTime = requestTime +
                (SystemClock.elapsedRealtime() - requestElapsedRealtime)

            check(response.length >= NTP_PACKET_SIZE) { "NTP响应长度无效: ${response.length}" }
            val leap = (request[0].toInt() ushr 6) and 0x3
            val mode = request[0].toInt() and 0x7
            val stratum = request[1].toInt() and 0xff
            check(leap != 3) { "NTP服务器时间未同步" }
            check(mode == 4 || mode == 5) { "NTP响应模式无效: $mode" }
            check(stratum in 1..15) { "NTP响应层级无效: $stratum" }

            val originateTime = readTimestamp(request, 24)
            val receiveTime = readTimestamp(request, 32)
            val transmitTime = readTimestamp(request, 40)
            check(abs(originateTime - requestTime) <= 1_000) { "NTP响应与请求不匹配" }
            check(receiveTime > 0 && transmitTime > 0) { "NTP响应时间无效" }

            val clockOffset = ((receiveTime - requestTime) +
                (transmitTime - responseTime)) / 2
            return responseTime + clockOffset
        }
    }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        var seconds = 0L
        var fraction = 0L
        for (index in 0 until 4) {
            seconds = (seconds shl 8) or (buffer[offset + index].toLong() and 0xff)
            fraction = (fraction shl 8) or (buffer[offset + 4 + index].toLong() and 0xff)
        }
        return (seconds - NTP_EPOCH_OFFSET_SECONDS) * 1_000 +
            fraction * 1_000 / 0x1_0000_0000L
    }

    private fun writeTimestamp(buffer: ByteArray, offset: Int, timeMillis: Long) {
        val seconds = timeMillis / 1_000 + NTP_EPOCH_OFFSET_SECONDS
        val fraction = timeMillis % 1_000 * 0x1_0000_0000L / 1_000
        for (index in 0 until 4) {
            buffer[offset + index] = (seconds ushr (24 - index * 8)).toByte()
            buffer[offset + 4 + index] = (fraction ushr (24 - index * 8)).toByte()
        }
    }

    fun formatBeijingTime(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date(timeMillis))
    }

    fun openDateTimeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openWriteSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun hasSetTimePermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.SET_TIME) == PackageManager.PERMISSION_GRANTED
    }

    private fun set24HourFormat(context: Context): Boolean {
        return Settings.System.canWrite(context) && Settings.System.putString(
            context.contentResolver,
            Settings.System.TIME_12_24,
            "24"
        )
    }
}
