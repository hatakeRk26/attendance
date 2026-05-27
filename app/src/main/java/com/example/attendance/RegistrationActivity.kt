package com.example.attendance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 登録画面用のレイアウトを指定
        setContentView(R.layout.activity_registration)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val editId = findViewById<EditText>(R.id.editStudentId)
        val editName = findViewById<EditText>(R.id.editName)

        btnRegister.setOnClickListener {
            val id = editId.text.toString()
            val name = editName.text.toString()

            if (id.isNotEmpty() && name.isNotEmpty()) {
                // データを保存
                UserDataManager.saveUser(this, id, name)

                // 判定待ち画面（MainActivity）へ戻る
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // 登録画面を閉じる
            } else {
                Toast.makeText(this, getString(R.string.error_empty_input), Toast.LENGTH_SHORT).show()
            }
        }
    }
}