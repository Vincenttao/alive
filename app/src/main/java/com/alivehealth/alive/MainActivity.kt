package com.alivehealth.alive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import com.alivehealth.alive.data.BottomNavItem
import com.alivehealth.alive.data.DailyExercise
import com.alivehealth.alive.data.DailyStatistics
import com.alivehealth.alive.data.ExerciseDataResult
import com.alivehealth.alive.data.ExerciseHistory
import com.alivehealth.alive.data.ExerciseInfo
import com.alivehealth.alive.data.GridItem
import com.google.gson.Gson
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.core.chart.column.ColumnChart
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale


private const val TAG="测试->MainActivity"




fun aggregateExerciseHistory(exerciseHistory: List<ExerciseHistory>): List<DailyStatistics> {
    val endDate = LocalDate.now().minusDays(1)
    val startDate = LocalDate.now().minusDays(6)

    val initialMap = (0..6).associate {
        startDate.plusDays(it.toLong()) to DailyStatistics(
            date = startDate.plusDays(it.toLong()).toString(),
            averageScore = 0.0,
            totalCount = 0
        )
    }.toMutableMap()

    val aggregatedData = exerciseHistory
        .groupBy { LocalDate.parse(it.date_completed) }
        .mapValues { (date,exercises) ->
            DailyStatistics(
                date = date.toString(),
                averageScore = exercises.map{ it.score }.average(),
                totalCount = exercises.size
            )
        }

    aggregatedData.forEach{(date, stat) ->
        initialMap[date] = stat
    }

    return initialMap.values.sortedBy { LocalDate.parse(it.date) }

}

fun convertToChartEntryModel(dailyStatistics: List<DailyStatistics>): Pair<ChartEntryModelProducer,ChartEntryModelProducer>{
    val completedCountsEntries = dailyStatistics.mapIndexed { index, stats ->
        entryOf(index.toFloat(), stats.totalCount.toFloat())
    }

    val averageScoresEntries = dailyStatistics.mapIndexed { index, stats ->
        entryOf(index.toFloat(), stats.averageScore.toFloat())
    }

    val completedCountsProducer = ChartEntryModelProducer(completedCountsEntries)
    val averageScoresProducer = ChartEntryModelProducer(averageScoresEntries)

    return Pair(completedCountsProducer, averageScoresProducer)

}




fun parseExerciseData(jsonString: String): ExerciseDataResult {
    val gson = Gson()
    val dailyExercise = gson.fromJson(jsonString, DailyExercise::class.java)
    val filteredExercises = dailyExercise.exercises_info.filter { it.completed == 0 }
    return ExerciseDataResult(dailyExercise.course_info, filteredExercises) // 过滤掉已完成的锻炼
}



private suspend fun fetchDailyExerciseData(context: Context, date: String, token: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val baseUrl = context.getString(R.string.server_base_url)
    val url = URL(baseUrl+"get_daily_exercise")
    //Log.d("MainActivity", "Make request: $url")

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
            //Log.d(TAG, "Request body: $requestJson")
            outputStream.use { it.write(requestJson.toString().toByteArray()) }

            // 获取响应码
            val responseCode = responseCode
            Log.d(TAG, "Response code: $responseCode")

            // 处理响应
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = inputStream.bufferedReader().use { it.readText() }
                //Log.d(TAG, "Response body: $responseBody")
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



private suspend fun fetchUserExerciseHistory(context: Context, start_date: String, end_date: String, token: String): List<ExerciseHistory> = withContext(Dispatchers.IO) {
    val baseUrl = context.getString(R.string.server_base_url)
    var exerciseHistoryList: List<ExerciseHistory> = listOf()

    // 定义重试次数和当前尝试次数
    val maxRetries = 3
    var currentTry = 0
    var success = false

    while (currentTry < maxRetries && !success) {
        try {
            val url = URL(baseUrl + "exercise_history")
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000 // 设置连接超时时间
                readTimeout = 15000 // 设置读取超时时间
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")

                val requestJson = JSONObject().apply {
                    put("start_date", start_date)
                    put("end_date", end_date)
                }
                outputStream.use { it.write(requestJson.toString().toByteArray()) }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = inputStream.bufferedReader().use { it.readText() }
                    val gson = Gson()
                    val rawList : List<ExerciseHistory> = gson.fromJson(responseBody,Array<ExerciseHistory>::class.java).toList()
                    exerciseHistoryList = rawList.map { exerciseHistory ->
                        val formattedDate = exerciseHistory.date_completed.split("T")[0]
                        exerciseHistory.copy(date_completed = formattedDate)
                    }

                    //exerciseHistoryList = gson.fromJson(responseBody, Array<ExerciseHistory>::class.java).toList()
                    Log.d(TAG, "Exercise history from $start_date to $end_date: $exerciseHistoryList")
                    success = true // 标记请求成功，退出循环
                } else {
                    Log.e(TAG, "Error fetching user exercise history: HTTP $responseCode")
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout during fetching user exercise history, retrying... Try: ${currentTry + 1}")
        } catch (e: Exception) {
            Log.e(TAG, "Error during fetching user exercise history: ${e.message}")
            break // 对于非超时异常，退出循环
        } finally {
            currentTry++
        }
    }

    if (!success) {
        Log.e(TAG, "Failed to fetch user exercise history after $maxRetries attempts.")
    }

    exerciseHistoryList
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


    private var lastSevenDayHistory by mutableStateOf<List<ExerciseHistory>?>(null)

    @Composable
    fun MainScreen(){
        /*
        Scaffold(
            bottomBar = { BottomNavigationBar()}
        ) {innerPadding ->
            BodyContent(Modifier.padding(innerPadding))

        }

         */

        BodyContent()
    }

    @Composable
    fun BodyContent(modifier: Modifier = Modifier) {
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var exercisePlan by remember { mutableStateOf<List<ExerciseInfo>>(listOf()) }
        var showExerciseDetails by remember{ mutableStateOf(false) }
        var courseName by remember { mutableStateOf("")}
        val scrollState = rememberScrollState()

        

        LaunchedEffect(selectedDate) {
            val sharedPreferences = getSharedPreferences(getString(R.string.SharedPreferences), MODE_PRIVATE)
            val token = sharedPreferences.getString("token", "")
            Log.d(TAG,"in MainScreen() get token:$token")
            if (!token.isNullOrEmpty()) {
                val (responseCode, responseBody) = fetchDailyExerciseData(this@MainActivity, selectedDate.toString(), token)
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 解析responseBody并更新exercisePlan
                    val parsedData = parseExerciseData(responseBody) // 根据您的JSON格式来解析数据
                    exercisePlan = parsedData.filteredExercises
                    courseName = parsedData.courseInfo.course_name
                    Log.d(TAG,"exercisePlan without history:$exercisePlan")
                }
            }
            if (!token.isNullOrEmpty()) {
                val exerciseHistory = fetchUserExerciseHistory(this@MainActivity, selectedDate.toString(),selectedDate.toString(), token)
                //更新exercisePlan，标记已完成和未完成的项目
                exercisePlan = exercisePlan.map{ exercise ->
                    val isCompleted = exerciseHistory.any { history ->
                        history.exercise_id == exercise.id && history.completed == 1
                    }
                    exercise.copy(completed = if (isCompleted) 1 else 0)
                }
                Log.d(TAG,"Exercise plan with history:$exercisePlan")
                exercisePlan = exercisePlan.sortedByDescending { it.completed }

                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val tempEndDate = LocalDate.now().minusDays(1)//昨天
                val tempStartDate = LocalDate.now().minusDays(7)
                val endDate = tempEndDate.format(formatter)
                val startDate = tempStartDate.format(formatter)
                lastSevenDayHistory = fetchUserExerciseHistory(this@MainActivity,startDate,endDate,token)
                Log.d(TAG,"LastSevenDay:$lastSevenDayHistory")

            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo

            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.alive_health_logo),
                contentDescription = "Logo"
            )

            FourGridScreen { destination ->
                when (destination) {
                    "HomeHealthAssessment" -> {
                        val intent = Intent(this@MainActivity, QuestionActivity::class.java)
                        startActivity(intent)
                    }

                    "MuscleStrengthAssessment" -> {
                        val intent = Intent(this@MainActivity, QuestionActivity::class.java)
                        // 你可以通过Intent传递额外的数据来区分不同的行为
                        // intent.putExtra("EXTRA_DATA", "Some data")
                        startActivity(intent)
                    }
                    // 其他case可以根据需要添加
                }
            }

            StatisticsChart(lastSevenDayHistory)

            WeekCalendar()

            TodayExerciseCard(
                courseName = courseName,
                exercisePlan = exercisePlan,
                showExerciseDetails = showExerciseDetails,
                onShowExerciseDetailsChange = {newValue ->
                    showExerciseDetails = newValue
                }
            )

            Divider(color = Color.LightGray, thickness = 1.dp,
                modifier = Modifier
                    .padding(8.dp))





            LogoutAndRecommendButtons()//退出登录和推荐课程按钮



        }
    }

    @Composable
    fun BottomNavigationBar(){
        var selectedItem by remember { mutableStateOf(BottomNavItem.Home) }
        val items = listOf(BottomNavItem.Home, BottomNavItem.Recommendations, BottomNavItem.Profile)

        NavigationBar {
            items.forEach { item ->
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title) },
                    selected = selectedItem == item,
                    onClick = { selectedItem = item }
                )
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
            for (i in 6 downTo 0) {
                val day = today.minusDays(i.toLong())
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
    fun StatisticsChart(exerciseHistory: List<ExerciseHistory>?){
        Log.d(TAG,"Statistics Start.")
        val aggregatedData = aggregateExerciseHistory(exerciseHistory?:listOf())
        Log.d(TAG,"aggregatedData:$aggregatedData")
        val (completedCountsProducer, averageScoresProducer) = convertToChartEntryModel(aggregatedData)
        Log.d(TAG,"completedCountsProducer:$completedCountsProducer")

        val columnStyle = LineComponent(
            color = Color.Gray.hashCode(),
            strokeWidthDp = 8f
        )


        Column {
            Text(
                text = "过去七天锻炼次数和评分情况",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Chart(
                chart = ColumnChart(
                    columns = listOf(columnStyle),
                    spacingDp = 8f,
                    innerSpacingDp = 4f,
                    mergeMode = ColumnChart.MergeMode.Grouped,

                ),
                chartModelProducer = completedCountsProducer,
                bottomAxis = rememberBottomAxis(),
                //modifier = Modifier.heightIn(max = 100.dp)
            )
            /*
            Chart(
                chart = com.patrykandpatrick.vico.compose.chart.line.lineChart(),
                chartModelProducer = averageScoresProducer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(),
            )

             */
        }

    }


    @Composable
    fun TodayExerciseCard(
        courseName: String,
        exercisePlan: List<ExerciseInfo>,
        showExerciseDetails: Boolean,
        onShowExerciseDetailsChange: (Boolean) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { onShowExerciseDetailsChange(!showExerciseDetails) },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "今日课程：$courseName",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val totalExercises = exercisePlan.size
                    val completedExercises = exercisePlan.count { it.completed == 1 }
                    val remainingExercises = totalExercises - completedExercises


                    // 今日待完成
                    Column(modifier = Modifier.weight(3f)) {

                        Text(
                            text = "今日待完成锻炼：$totalExercises 条",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "未完成：$remainingExercises 条",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }



                    VerticalDivider()

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("点击查看", style = MaterialTheme.typography.bodyLarge)

                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "查看详情",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            if (showExerciseDetails) {
                exercisePlan.forEach{exerciseInfo ->
                    ScheduleCard(exerciseInfo, exerciseInfo.completed == 1 )
                }
            }
        }
    }


    @Composable
    fun ScheduleCard(exerciseInfo: ExerciseInfo, isCompleted: Boolean) {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable(enabled = !isCompleted) {
                    if (!isCompleted) {
                        val intent = Intent(context, ExerciseActivity::class.java)
                        intent.putExtra("exerciseId", exerciseInfo.id)
                        Log.d(TAG, "ExerciseInfo:$exerciseInfo")
                        Log.d(TAG, "Intent:$intent")
                        context.startActivity(intent)
                    }

                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ){
                Text(text = exerciseInfo.name)
                Text(
                    text = if(isCompleted) "已完成" else "(未完成，点击开始)",
                    color = if(isCompleted) Color.Gray else Color.Black
                )
            }

            Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    @Composable
    fun VerticalDivider(){
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .background(Color.LightGray)
        )
    }


    @Composable
    fun FourGridScreen(onNavigate: (String) -> Unit) {
        // 示例数据，替换为你自己的图标和标题
        val context = LocalContext.current
        val items = listOf(
            GridItem("居家健康评估", R.drawable.home_health_assessment) {
                onNavigate("HomeHealthAssessment")
            },
            GridItem("肌肉力量评估", R.drawable.muscle_strength_assessment) {
                onNavigate("MuscleStrengthAssessment")
            },
            GridItem("病例管理", R.drawable.case_assistant) {
                Toast.makeText(context, "敬请期待", Toast.LENGTH_LONG).show()
            },
            GridItem("基础病管理", R.drawable.basic_disease_management) {
                Toast.makeText(context, "敬请期待", Toast.LENGTH_LONG).show()
            }
        )

        val rows = items.chunked(2)

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowItems.forEach { item ->
                        GridItemCard(item = item)
                    }
                }
            }
        }
    }

    @Composable
    fun GridItemCard(item: GridItem) {
        Card(
            modifier = Modifier
                .padding(4.dp)
                .clickable { item.onClick() }, // 添加点击事件
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Image(
                    painter = painterResource(id = item.icon),
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(72.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    fun LogoutAndRecommendButtons() {
        val context = LocalContext.current // 获取当前 Compose 的 Context

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    // 获取SharedPreferences编辑器
                    val sharedPreferences = context.getSharedPreferences(context.getString(R.string.SharedPreferences), Context.MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    // 清除token
                    editor.remove("token")
                    editor.apply()

                    // 返回到登录屏幕
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    (context as? Activity)?.finish() // 安全转换为 Activity 并调用 finish()
                },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(end = 8.dp)
                    .weight(1f)
            ) {
                Text(text = "退出登录")
            }

        }
    }



}
