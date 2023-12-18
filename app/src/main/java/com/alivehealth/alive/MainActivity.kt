package com.alivehealth.alive

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val sharedPreferences = getSharedPreferences(getString(R.string.SharedPreferences), MODE_PRIVATE)
            val isLoggedIn = sharedPreferences.contains("token")

            if (isLoggedIn) {
                MainScreen()
            } else {
                // Navigate to Login Activity
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    @Preview
    @Composable
    fun PreviewMainScreen() {
        MainScreen()
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.alive_health_logo),
                contentDescription = "Logo"
            )

            // Calendar
            WeekCalendar()

            // Schedule
            // Replace with actual data fetching logic
            val schedules = listOf("Meeting at 10 AM", "Lunch at 12 PM", "Report Submission by 5 PM")
            schedules.forEach { schedule ->
                ScheduleCard(schedule)
            }
            //分割线
            Divider(color = Color.LightGray, thickness = 1.dp)

            //按钮

            Button(
                onClick = {
                    startActivity(Intent(this@MainActivity, QuestionActivity::class.java))
                },
                modifier = Modifier.padding(top = 16.dp)
            ){
                Text(text = "为我推荐课程")
            }

            Button(
                onClick = {
                    // 获取SharedPreferences编辑器
                    val sharedPreferences = getSharedPreferences(getString(R.string.SharedPreferences), MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    // 清除token
                    editor.remove("token")
                    editor.apply()

                    // 返回到登录屏幕
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    // 结束当前的MainActivity
                    finish()
                },
                modifier = Modifier.padding(top = 16.dp)
            ){
                Text(text = "退出登录")
            }


        }
    }

    @Composable
    fun WeekCalendar() {
        val today = LocalDate.now()
        var selectedDate by remember { mutableStateOf(today) } // 记录选中的日期

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0..6) {
                val day = today.plusDays(i.toLong())
                DayCard(day, day == selectedDate) {
                    selectedDate = day // 更新选中的日期
                }
            }
        }
    }

    @Composable
    fun DayCard(day: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
        val textColor = if (isSelected) Color(0xFFFFA500) else Color.Black // 橙色或黑色

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(4.dp)
        ) {
            Text(
                text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE),
                color = textColor
            )
            Text(
                text = day.dayOfMonth.toString(),
                color = textColor
            )
        }
    }

    @Composable
    fun ScheduleCard(schedule: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable {
                    // Start another activity on card click
                    startActivity(Intent(this@MainActivity, DetailActivity::class.java))
                }
        ) {
            Text(
                text = schedule,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
