package com.alivehealth.alive

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置按钮点击事件监听器，启动恢复课程

        val startButton: Button = findViewById(R.id.start_recovery_course)
        startButton.setOnClickListener {
            // 创建一个Intent来启动MotionDetectActivity
            val intent = Intent(this, MotionDetectActivity::class.java)
            // 启动活动
            startActivity(intent)
        }
        val gridView: GridView = findViewById(R.id.courses_grid)

        val courses = arrayOf("课程1", "课程2", "课程3", "课程4", "课程5", "课程6", "课程7", "课程8", "课程9")
        val adapter = GridItemAdapter(this, courses)

        gridView.adapter = adapter

        gridView.setOnItemClickListener { _, _, position, _ ->
            Toast.makeText(this, "点击了: ${courses[position]}", Toast.LENGTH_SHORT).show()
        }
    }
}


