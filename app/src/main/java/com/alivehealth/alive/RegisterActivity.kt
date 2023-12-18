package com.alivehealth.alive

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alivehealth.alive.databinding.RegisterBinding
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: RegisterBinding
    val TAG = "Register 测试"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRegister.setOnClickListener {
            val username = binding.editTextRegisterUsername.text.toString().trim()
            val password = binding.editTextRegisterPassword.text.toString().trim()
            val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "用户名和密码不能为空", Toast.LENGTH_SHORT).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, "两次输入的密码不匹配", Toast.LENGTH_SHORT).show()
            } else {
                register(username, password)
            }
        }

        binding.textViewGoToLogin.setOnClickListener {
            // 创建一个Intent来启动LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            // 启动LoginActivity
            startActivity(intent)
        }
    }

    private fun register(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = makeRegisterRequest(username, password)
            withContext(Dispatchers.Main) {
                if (response.first == HttpURLConnection.HTTP_CREATED) {
                    Toast.makeText(this@RegisterActivity, "注册成功", Toast.LENGTH_SHORT).show()
                    // 可能的逻辑：关闭活动或跳转到登录界面
                    val loginIntent = Intent(this@RegisterActivity, LoginActivity::class.java)
                    startActivity(loginIntent)
                    finish() // 关闭当前活动

                } else {
                    Toast.makeText(this@RegisterActivity, "注册失败：${response.second}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun makeRegisterRequest(username: String, password: String): Pair<Int, String> {
        val baseUrl = getString(R.string.server_base_url)
        val url = URL(baseUrl + "register")
        (url.openConnection() as HttpURLConnection).apply {
            try {
                Log.d(TAG,"启动注册：$url")
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                val requestJson = JSONObject()
                requestJson.put("username", username)
                requestJson.put("password", password)
                outputStream.use { it.write(requestJson.toString().toByteArray()) }
                val responseCode = responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream.bufferedReader().use { it.readText() }
                } else {
                    errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                }
                return responseCode to response
            } catch (e: Exception) {
                Log.e(TAG, "Error during register request: ${e.message}")
                e.printStackTrace()
            } finally {
                disconnect()
            }
        }
        return 0 to "Error"
    }
}