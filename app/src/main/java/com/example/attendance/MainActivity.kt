package com.example.attendance

import UserDataManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView

    // --- 結果受信機 ---
    private val attendanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("Attendance", "MainActivity: 放送を受信しました！")
            val response = intent?.getStringExtra("result") ?: ""
            Log.d("Attendance", "MainActivity: 受信データ: $response")

            // ★ 受け取ったステータスに応じて段階的に画面を更新
            when {
                // ① 対象Wi-Fiを探している段階（未接続、または別Wi-Fi）
                response == "SEARCHING" -> {
                    textStatus.text = "Wi-Fiを検知中..."
                    textStatus.setTextColor(Color.GRAY)
                }

                // ② 対象Wi-Fiを検知し、出席通信を行っている段階
                response == "WAITING" -> {
                    textStatus.text = "出席待機中..."
                    textStatus.setTextColor(Color.rgb(255, 140, 0)) // 視認性の良いオレンジ色
                }

                // ③ 出席完了
                response.startsWith("SUCCESS") -> {
                    textStatus.text = getString(R.string.status_success)
                    textStatus.setTextColor(Color.GREEN)
                }

                // サーバーから返信がない場合（タイムアウト）
                response == "TIMEOUT" -> {
                    textStatus.text = "応答なし (再接続待機中...)"
                    textStatus.setTextColor(Color.RED)
                }

                // その他エラー
                else -> {
                    textStatus.text = getString(R.string.status_error_format, response)
                    textStatus.setTextColor(Color.RED)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserDataManager.isRegistered(this)) {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        // 1. 画面上のパーツを取得
        val textUserInfo = findViewById<TextView>(R.id.textUserInfo)
        textStatus = findViewById(R.id.tvStatusValue) // ここで初期化

        // 2. 保存されている情報を表示
        val studentId = UserDataManager.getStudentId(this)
        val userName = UserDataManager.getUserName(this)

        // 3. 画面にセット
        val infoText = getString(R.string.user_info_format, studentId, userName)
        textUserInfo.text = infoText

        // 4. 起動時のステータス初期表示
        textStatus.text = "Wi-Fiを検知中..."
        textStatus.setTextColor(Color.GRAY)

        // --- サービスを起動する ---
        Log.d("Attendance", "MainActivity: サービス起動を試みます")
        val serviceIntent = Intent(this, AttendanceService::class.java)
        startForegroundService(serviceIntent)

        // --- レシーバー登録 (Android 14のエラー対策済) ---
        val filter = IntentFilter("com.example.attendance.ATTENDANCE_RESULT")
        ContextCompat.registerReceiver(
            this,
            attendanceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // --- 終了処理 ---
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(attendanceReceiver)
        } catch (e: Exception) {
            // 解除済みの場合の回避
        }
    }
}