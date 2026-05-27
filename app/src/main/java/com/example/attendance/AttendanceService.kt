package com.example.attendance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AttendanceService : Service() {

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ================= 設定項目 =================
    private val serverIp = "192.168.77.5"
    private val serverPort = 7777
    private val channelId = "attendance_channel"
    private val notificationId = 1
    // ===========================================

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Attendance", "Service onStartCommand called") // これが出るか確認
        startForeground(notificationId, createNotification("Wi-Fi接続を待機中..."))
        registerWifiObserver()
        return START_STICKY
    }

    private fun registerWifiObserver() {
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            // Wi-Fi接続が利用可能になった時に呼ばれる
            override fun onAvailable(network: Network) {
                Log.d("Attendance", "onAvailable: Message 4 送信完了を検知")
                updateNotification("出席情報を送信中...")
                thread {
                    // 通信が安定するまで少し待ってから送信
                    Thread.sleep(300)
                    sendAttendancePacket() // ここでUDP送信を実行
                }
            }
        }

        networkCallback?.let {
            connectivityManager.registerNetworkCallback(request, it)
        }
    }

    private fun sendAttendancePacket() {
        try {
            val studentId = UserDataManager.getStudentId(this)
            val userName = UserDataManager.getUserName(this)
            val message = "$studentId,$userName".toByteArray()

            val address = InetAddress.getByName(serverIp)

            DatagramSocket().use { socket ->
                repeat(2) {
                    val packet = DatagramPacket(message, message.size, address, serverPort)
                    socket.send(packet)
                    Thread.sleep(1000)
                }
            }

            updateNotification("出席送信完了: $userName")
        } catch (e: Exception) {
            Log.e("Attendance", "Send error", e)
            updateNotification("送信エラーが発生しました")
        }
    }

    private fun createNotification(contentText: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "出席確認通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "出席管理システムの動作状態を表示します"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("出席管理システム")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}