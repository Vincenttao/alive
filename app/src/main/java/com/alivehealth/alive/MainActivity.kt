package com.alivehealth.alive

import android.content.Context
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
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG="测试->MainActivity"

data class ExerciseInfo(
    val id:Int,
    val name: String,
    val sequence: Int,
    val completed: Int
)

data class DailyExercise(
    val course_info: CourseInfo,
    val exercises_info: List<ExerciseInfo>
)

data class CourseInfo(
    val course_id: Int,
    val course_name: String,
    val start_date: String,
    val frequency: String
)

fun parseExerciseData(jsonString: String): List<ExerciseInfo> {
    val gson = Gson()
    val dailyExercise = gson.fromJson(jsonString, DailyExercise::class.java)
    return dailyExercise.exercises_info.filter { it.completed == 0 } // 过滤掉已完成的锻炼
}


private suspend fun fetchDailyExerciseData(context: Context, date: String, token: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val baseUrl = context.getString(R.string.server_base_url)
    val url = URL(baseUrl+"get_daily_exercise")
    Log.d("MainActivity", "Make request: $url")

    var responsePair: Pair<Int, String> = 0 to "Initial response"

    (url.openConnection() as HttpURLConnection).apply {
        try {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")

            // 构建请求体
            val requestJson = JSONObject().apply {
                put("date", date)
            }
            Log.d(TAG, "Request body: $requestJson")
            outputStream.use { it.write(requestJson.toString().toByteArray()) }

            // 获取响应码
            val responseCode = responseCode
            Log.d(TAG, "Response code: $responseCode")

            // 处理响应
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Response body: $responseBody")
                responseBody
            } else {
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Error response body: $errorBody")
                errorBody
            }

            responsePair = responseCode to response
        } catch (e: Exception) {
           val sw = StringWriter()
           val pw = PrintWriter(sw)
           e.printStackTrace(pw)
            val stackTraceString = sw.toString()
            Log.e("MainActivity", "Error during fetching daily exercise data: ${e.message}")
            Log.e("MainActivity", "Stack trace: $stackTraceString")
            responsePair = 0 to "Error during fetching daily exercise data: ${e.message}\nStack trace: $stackTraceString"
        } finally {
            disconnect()
        }
    }

    responsePair
}

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
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var exercisePlan by remember { mutableStateOf<List<ExerciseInfo>>(listOf()) }

        LaunchedEffect(selectedDate) {
            val sharedPreferences = getSharedPreferences(getString(R.string.SharedPreferences), MODE_PRIVATE)
            val token = sharedPreferences.getString("token", "")
            Log.d(TAG,"in MainScreen() get token:$token")
            if (!token.isNullOrEmpty()) {
                val (responseCode, responseBody) = fetchDailyExerciseData(this@MainActivity, selectedDate.toString(), token)
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 解析responseBody并更新exercisePlan
                    val parsedData = parseExerciseData(responseBody) // 根据您的JSON格式来解析数据
                    exercisePlan = parsedData
                }
            }
        }

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

            Button(
                onClick = {
                    startActivity(Intent(this@MainActivity, QuestionActivity::class.java))
                },
                modifier = Modifier.padding(top = 16.dp)
            ){
                Text(text = "为我推荐课程")
            }


            Divider(color = Color.LightGray, thickness = 1.dp)

            // Calendar
            WeekCalendar()

            // Schedule
            // Replace with actual data fetching logic

            LazyColumn{
                items(exercisePlan.size) { index ->
                    val exerciseInfo = exercisePlan[index]
                    ScheduleCard(exerciseInfo)
                }
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
    fun ScheduleCard(exerciseInfo: ExerciseInfo) {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable {
                    val intent = Intent(context,ExerciseActivity::class.java)
                    intent.putExtra("exerciseId",exerciseInfo.id)
                    Log.d(TAG,"ExerciseInfo:$exerciseInfo")
                    Log.d(TAG,"Intent:$intent")
                    context.startActivity(intent)
                }
        ) {
            Text(
                text = exerciseInfo.name,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

}
