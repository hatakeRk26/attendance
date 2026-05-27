package com.example.attendance

import UserDataManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserDataManager.isRegistered(this)) {
            // 未登録なら登録画面へ移動
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
            finish() // MainActivityを終了させて戻れないようにする
            return
        }
        setContentView(R.layout.activity_main)

        // 1. 画面上のパーツを取得
        val textUserInfo = findViewById<TextView>(R.id.textUserInfo)

        // 2. 保存されている情報を表示
        val studentId = UserDataManager.getStudentId(this)
        val userName = UserDataManager.getUserName(this)

        // 3. 画面にセット
        val infoText = getString(R.string.user_info_format, studentId, userName)
        textUserInfo.text = infoText
    }
}