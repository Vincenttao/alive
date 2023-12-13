package com.alivehealth.alive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alivehealth.alive.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var loginActivityResultLauncher: ActivityResultLauncher<Intent>



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loginActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 登录成功
                // 你的逻辑代码
                val token = getToken()
                binding.mainLoginTextView.text = token ?: "No token found"

            } else {
                // 登录失败或用户取消登录
                finish() // 如果你希望在登录失败时关闭MainActivity
            }
        }

        if (!isLoggedIn()) {
            Toast.makeText(this, "没有找到token，请登录", Toast.LENGTH_SHORT).show()
            val loginIntent = Intent(this, LoginActivity::class.java)
            loginActivityResultLauncher.launch(loginIntent)
            return
        }



        /*
        // 设置按钮点击事件监听器，启动恢复课程

        val startButton: Button = findViewById(R.id.start_recovery_course)
        startButton.setOnClickListener {
            // 创建一个Intent来启动MotionDetectActivity
            val intent = Intent(this, CourseActivity::class.java)
            intent.putExtra("COURSE_CODE", "course1")
            // 启动活动
            startActivity(intent)
        }
        val gridView: GridView = findViewById(R.id.courses_grid)

        val courses = arrayOf("瑜伽姿势tree", "课程2", "课程3", "课程4", "课程5", "课程6", "课程7", "课程8", "课程9")
        val courseCodes = arrayOf("course1", "sun", "moon", "tree","tree","tree","tree","tree","tree")
        val adapter = GridItemAdapter(this, courses)

        gridView.adapter = adapter

        gridView.setOnItemClickListener { _, _, position, _ ->
            Toast.makeText(this, "点击了: ${courses[position]}", Toast.LENGTH_SHORT).show()
            // 创建一个Intent来启动MotionDetectActivity
            val intent = Intent(this, CourseActivity::class.java)
            // 使用 putExtra 方法将对应的课程代码传递给MotionDetectActivity
            intent.putExtra("COURSE_CODE", courseCodes[position])
            // 启动活动
            startActivity(intent)
        }

         */


    }

    private fun isLoggedIn(): Boolean {
        val sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null)
        return token != null && token.isNotBlank()

    }

    private fun getToken(): String?{
        val sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null)
        return token
    }


}


