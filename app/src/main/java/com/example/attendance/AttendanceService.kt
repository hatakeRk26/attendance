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
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Timer
import java.util.TimerTask

class AttendanceService : Service() {

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val serverIp = "192.168.77.5"
    private val serverPort = 7777
    private val targetSubnetPrefix = "192.168.77." // 研究室ネットワーク判定用
    private val channelId = "attendance_channel"
    private val notificationId = 1

    // ★ 30秒おきの定期実行用タイマー
    private var pollingTimer: Timer? = null

    // ★多重送信防止・状態管理用のフラグ
    @Volatile private var isSending = false
    @Volatile private var isAlreadyPresent = false

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Attendance", "Service: onStartCommand - サービス開始")
        startForeground(notificationId, createNotification("Wi-Fiを検知中..."))

        // 起動時に画面を「検知中」に設定
        sendBroadcastToActivity("SEARCHING")

        registerWifiObserver()
        return START_STICKY
    }

    private fun registerWifiObserver() {
        Log.d("Attendance", "Observer: Wi-Fi監視を登録します")
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Log.d("Attendance", "NetworkCallback: Wi-Fiを検知(onLinkPropertiesChanged)")

                val ipv4Address = linkProperties.linkAddresses
                    .map { it.address }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull()?.hostAddress

                Log.d("Attendance", "NetworkCallback: 取得IP = $ipv4Address")

                if (ipv4Address != null && ipv4Address.startsWith(targetSubnetPrefix)) {
                    Log.d("Attendance", "対象Wi-Fi検知: 30秒おきの出席・状態確認ループを開始します。")

                    // すでにタイマーが動いていなければ、30秒おきの定期送信を開始
                    startPolling(network)

                } else {
                    Log.d("Attendance", "対象外のWi-Fiです。タイマーを停止し待機します。")
                    stopPolling()
                    sendBroadcastToActivity("SEARCHING")
                    updateNotification("対象Wi-Fiを検知中...")
                }
            }

            override fun onLost(network: Network) {
                Log.d("Attendance", "Wi-Fi切断を検知")
                // 切断されたらタイマー停止＆状態リセット
                stopPolling()
                isAlreadyPresent = false
                isSending = false

                sendBroadcastToActivity("SEARCHING")
                updateNotification("Wi-Fi切断: Wi-Fiを検知中...")
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    // ==========================================
    // ★ 30秒おきの定期送信（ポーリング）管理
    // ==========================================
    private fun startPolling(network: Network) {
        if (pollingTimer != null) {
            return
        }

        pollingTimer = Timer().apply {
            // ★ scheduleAtFixedRate を schedule に変更するだけ
            schedule(object : TimerTask() {
                override fun run() {
                    if (isAlreadyPresent) {
                        Log.d("Attendance", "出席完了済みの為、定期ポーリングをスキップします")
                        return
                    }
                    if (isSending) {
                        Log.d("Attendance", "前回の通信がまだ処理中のためスキップします")
                        return
                    }

                    isSending = true
                    try {
                        Log.d("Attendance", "【定期送信】サーバーへ状態確認・出席リクエストを送信します...")
                        sendAttendanceWithResponse(network)
                    } finally {
                        isSending = false
                    }
                }
            }, 0, 30000) // 初回は即時(0)、次回以降は30秒後
        }
    }

    private fun stopPolling() {
        pollingTimer?.cancel()
        pollingTimer = null
        Log.d("Attendance", "定期ポーリングを停止しました")
    }

    // 画面（MainActivity）へ結果・状態を送信するヘルパー関数
    private fun sendBroadcastToActivity(status: String) {
        val broadcastIntent = Intent("com.example.attendance.ATTENDANCE_RESULT")
        broadcastIntent.putExtra("result", status)
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)
    }

    private fun sendAttendanceWithResponse(network: Network) {
        val studentId = UserDataManager.getStudentId(this)
        val userName = UserDataManager.getUserName(this)

        // 出席がまだなら「ID,名前」、出席完了後や状態確認のみなら "CHECK" などを送ることも可能ですが
        // サーバー側で "ID,名前" を受け取った際に時間外なら "FALSE" を返す仕様にしているため、そのまま送信します。
        val messageStr = "$studentId,$userName"
        val message = messageStr.toByteArray()

        try {
            val address = InetAddress.getByName(serverIp)

            DatagramSocket().use { socket ->
                network.bindSocket(socket)
                socket.soTimeout = 5000 // 5秒タイムアウト

                // 1. 送信
                val sendPacket = DatagramPacket(message, message.size, address, serverPort)
                socket.send(sendPacket)
                Log.d("Attendance", "UDP: パケット送信成功 -> $messageStr")

                // 2. サーバーからの返信を受信
                val buffer = ByteArray(1024)
                val receivePacket = DatagramPacket(buffer, buffer.size)

                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length).trim()
                    Log.i("Attendance", "UDP: サーバー応答受信 -> $response")

                    sendBroadcastToActivity(response)

                    // サーバーからの応答に応じた分岐
                    if (response.startsWith("SUCCESS")) {
                        isAlreadyPresent = true // 出席確定！以後ポーリングでの再送をストップ
                        updateNotification("出席が確認されました: $userName")
                        stopPolling() // 出席できたらタイマーを止めてもOK
                    } else if (response.contains("FALSE") || response.contains("時間外")) {
                        isAlreadyPresent = false
                        updateNotification("⏳ 時間外です（30秒おきに再確認中...）")
                        Log.w("Attendance", "現在は時間外です。次の30秒後に再試行します。")
                    } else {
                        isAlreadyPresent = false
                        updateNotification("通知: $response")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w("Attendance", "UDP: サーバー応答タイムアウト（サーバーが起動していない可能性）")
                    sendBroadcastToActivity("TIMEOUT")
                    updateNotification("サーバー応答なし（再接続待機中...）")
                }
            }
        } catch (e: Exception) {
            Log.e("Attendance", "通信エラー", e)
            sendBroadcastToActivity("ERROR: ${e.message}")
            updateNotification("送信エラー: ${e.message}")
        }
    }

    private fun createNotification(contentText: String): Notification {
        val channel = NotificationChannel(
            channelId, "出席確認通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "出席管理システムの動作状態を表示します"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

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
        stopPolling()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}