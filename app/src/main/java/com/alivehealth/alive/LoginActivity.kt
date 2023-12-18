package com.alivehealth.alive

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alivehealth.alive.databinding.LoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: LoginBinding
    private val TAG = "测试"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLogin.setOnClickListener {
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                login(username, password)
            } else {
                Toast.makeText(this, "用户名和密码不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        binding.textViewRegister.setOnClickListener {
            val registerIntent = Intent(this,RegisterActivity::class.java)
            startActivity(registerIntent)
        }
    }

    private fun login(username: String, password: String) {
        Log.d(TAG,"开始尝试登录，用户名： $username")
        CoroutineScope(Dispatchers.IO).launch {
            val response = makeLoginRequest(username, password)
            withContext(Dispatchers.Main) {
                if (response.first == HttpURLConnection.HTTP_OK) {
                    // 登录成功逻辑
                    // 解析响应中的token
                    val responseBody = JSONObject(response.second)
                    val token = responseBody.getString("token")

                    // 存储token，显示登录成功信息，跳转到主界面等
                    Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()

                    saveToken(token)
                    val mainIntent = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(mainIntent)

                    finish()
                } else {
                    val toast = Toast.makeText(this@LoginActivity, "用户名或者密码错误", Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                    toast.show()

                }
            }
        }
    }

    private fun makeLoginRequest(username: String, password: String): Pair<Int, String> {
        val baseUrl = getString(R.string.server_base_url)
        val url = URL(baseUrl + "login")
        Log.d(TAG,"尝试发送登录请求，地址：$url")
        (url.openConnection() as HttpURLConnection).apply {
            try {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                val requestJson = JSONObject()
                requestJson.put("username", username)
                requestJson.put("password", password)
                outputStream.use { it.write(requestJson.toString().toByteArray()) }
                val responseCode = responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "登录成功：$responseCode")
                    inputStream.bufferedReader().use { it.readText() }
                } else {
                    Log.d(TAG, "登录不成功：$responseCode")
                    errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                }
                Log.d(TAG,"response:$response")
                return responseCode to response
            } catch (e: Exception) {
                Log.e(TAG, "Error during login request: ${e.message}")
                e.printStackTrace()
            } finally {
                Log.d(TAG,"Disconnect")
                disconnect()
            }
        }
        return 0 to "Error"
    }

    private fun saveToken(token: String) {
        val sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("token", token)
        editor.apply()
    }

}




//        假设登录成功后的某个地方
//        setResult(RESULT_OK)
//        finish()