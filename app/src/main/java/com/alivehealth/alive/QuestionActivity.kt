package com.alivehealth.alive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG="测试->QuestionActivity"

class QuestionActivity : ComponentActivity() {
    private lateinit var recommendations: Map<String, Recommendation>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //初始化ViewModel，加载问题
        val viewModel: QuestionViewModel by viewModels()
        val questionsData = loadQuestionsFromJson()
        viewModel.setQuestions(questionsData.questions) // 假设 viewModel 只处理问题列表
        viewModel.navigationEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                if(it == "MainActivity") {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
        val logic = questionsData.logic.answerSequence
        recommendations = questionsData.recommendations


        setContent {
            MaterialTheme {
                QuestionScreen(viewModel, logic)
            }
        }
    }



    private fun loadQuestionsFromJson(): QuestionsData {
        val gson = Gson()
        val jsonFile = resources.openRawResource(R.raw.questions) // JSON 文件位于 res/raw/questions.json
        val reader = InputStreamReader(jsonFile)
        val questionsDataType = object : TypeToken<QuestionsData>() {}.type
        val questionsData: QuestionsData = gson.fromJson(reader, questionsDataType)
        reader.close()
        Log.d(TAG, "Json data loaded: ${questionsData.questions.size} questions")
        Log.d(TAG,"Json object:$questionsData")
        Log.d(TAG, "Loaded logic data: ${questionsData.logic.answerSequence}")
        return questionsData
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }


    @Composable
    fun QuestionScreen(viewModel: QuestionViewModel = viewModel(), logic: Map<String, String>) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = R.drawable.backgroud_svg),
                contentDescription = "Background",
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(
                    text = "Alive Health健康检测",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 36.sp // 标题大小
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Color.Transparent)
                ) {
                    Text(
                        text = "请您根据自己的情况，选择下面问题的答案（是或否），我们将根据您的情况，为您推荐适合的套餐",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                val question = viewModel.currentQuestion
                question?.let {
                    Log.d(TAG, "Displaying question card")
                    QuestionCard(question = (it), onOptionSelected = { nextId, answer ->
                        viewModel.onOptionSelected(nextId, answer)
                    })
                } ?: run {
                    val recommendation = viewModel.getRecommendation((logic))
                    Log.d(TAG, "问答结束。推荐课程：${recommendation ?: "无"}")

                    if (recommendation != null) {
                        val courseId = recommendations[recommendation]?.courseId
                        Log.d(TAG, "Recommendation:$courseId")

                        ConfirmAddToListDialog(
                            courseId = courseId ?: "",
                            onConfirm = { newCourseId ->
                                viewModel.addToList(this@QuestionActivity, newCourseId)
                            },
                            onCancel = {
                                navigateToMainActivity()
                            }
                        )
                    } else {
                        Text("问答结束。推荐课程:无")
                    }
                }
                if (viewModel.showDialog) {
                    OverwriteCourseDialog(viewModel)
                }
            }

        }
    }


    @Composable
    fun QuestionCard(question: QuestionItem, onOptionSelected: (Int?, String) -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(

                    text = question.text,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    question.options.forEach { option ->
                        Button(
                            onClick = { onOptionSelected(option.nextQuestion, option.text) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(option.text)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ConfirmAddToListDialog(
        courseId: String,
        onConfirm: (String) -> Unit,
        onCancel:() -> Unit
    ) {
        // 使用对话框组件询问用户
        AlertDialog(
            onDismissRequest = {/*话题关闭时处理方法*/},
            title = { Text("添加课程到每日清单") },
            text = { Text("是否要将课程 $courseId 添加到您的每日清单中？") },
            confirmButton = {
                Button(onClick = {
                    onConfirm(courseId)
                    //调用viewModel.addToList方法
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                Button(onClick = onCancel) {
                    Text("取消")
                }
            }
        )
    }

    @Composable
    fun OverwriteCourseDialog(viewModel: QuestionViewModel) {
        Log.d(TAG,"Overwrite dialog displaying")
        AlertDialog(
            onDismissRequest = { viewModel.showDialog = false },
            title = { Text("覆盖课程") },
            text = { Text("此课程已在您的清单中。是否要覆盖它？") },
            confirmButton = {
                Button(onClick = {
                    Log.d(TAG,"onClick")
                    viewModel.overwriteCourse(this@QuestionActivity)
                    viewModel.showDialog = false
                }) {
                    Text("覆盖")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }




}



data class QuestionsData(
    val questions: List<QuestionItem>,
    val recommendations: Map<String, Recommendation>,
    val logic: Logic
)

data class Recommendation(
    val courseId: String,
    val description: String
)

data class Logic(
    val answerSequence: Map<String, String>
)

data class QuestionItem(
    val id: Int,
    val text: String,
    val options: List<Option>
)

data class Option(
    val text: String,
    val nextQuestion: Int?
)

// 用于表示单次事件的类
class Event<out T>(private val content: T) {
    private var hasBeenHandled = false

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}



class QuestionViewModel : ViewModel() {
    var currentQuestion by mutableStateOf<QuestionItem?>(null)
        private set

    private var questionList: List<QuestionItem> = listOf()
    private var currentQuestionIndex = 0

    private var userAnswers = mutableListOf<String?>()

    var showDialog by mutableStateOf(false)
    private var tempCourseId: String? = null

    val navigationEvent = MutableLiveData<Event<String>>() //用于页面跳转的事件


    private fun showOverwriteDialog(courseId: String) {
        showDialog = true
        tempCourseId = courseId
    }




    fun setQuestions(questions: List<QuestionItem>) {
        questionList = questions
        currentQuestionIndex = 0
        currentQuestion = questionList.firstOrNull()

        //根据问题数量初始化 userAnswers
        userAnswers = MutableList(questions.size) {"None"}
        Log.d(TAG, "Questions set: ${questionList.size} questions")
    }

    fun onOptionSelected(nextQuestionId: Int?, answer: String) {
        val formattedAnswer = when(answer) {
            "是" -> "1"
            "否" -> "0"
            else -> "None"
        }
        if (currentQuestionIndex in userAnswers.indices) {
            userAnswers[currentQuestionIndex] = formattedAnswer
            Log.d(TAG, "Answer recorded for question $currentQuestionIndex: $answer -> $formattedAnswer")
        }

        nextQuestionId?.let { nextId ->
            currentQuestionIndex = questionList.indexOfFirst { question -> question.id == nextId }
            currentQuestion = questionList.getOrNull(currentQuestionIndex)
            Log.d(TAG, "Next question index: $currentQuestionIndex")
        } ?: run {
            // 当没有下一个问题时，这里可以处理结束逻辑
            currentQuestion = null
            Log.d(TAG, "No more questions. Quiz ended.")
        }
    }

    fun getRecommendation(logic: Map<String,String>): String? {
        val answerSequence = userAnswers.joinToString(separator = "")
        Log.d(TAG,"Answer sequence is $answerSequence")

        val recommendationResult = logic[answerSequence]
        Log.d(TAG,"Recommendation is $recommendationResult")
        return logic[answerSequence]
    }

    fun addToList(context: Context, courseId: String) {
        Log.d(TAG, "addToList function is called")
        viewModelScope.launch {
            Log.d(TAG, "Inside viewModelScope.launch")
            // 从SharedPreferences加载Token
            val (token, _) = loadCredentials(context)
            Log.d(TAG, "Token loaded: $token")
            Log.d(TAG, "Network request on thread: ${Thread.currentThread().name}")
            if (token != null) {
                val response = makeCourseRequest(context, courseId, "add_course_to_user", token)
                Log.d(TAG, "Response code: ${response.first}, Response body: ${response.second}")
                when (response.first) {
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                        Toast.makeText(context, "课程成功添加到用户", Toast.LENGTH_LONG).show()
                        navigationEvent.postValue(Event("MainActivity")) // 触发跳转到 MainActivity
                    }
                    HttpURLConnection.HTTP_CONFLICT -> {
                        showOverwriteDialog(courseId) // 确保覆盖对话框也使用Token
                    }
                    else -> {
                        Toast.makeText(context, "Error: ${response.second}", Toast.LENGTH_LONG).show()
                        navigationEvent.postValue(Event("MainActivity")) // 触发跳转到 MainActivity
                    }
                }
            } else {
                Toast.makeText(context, "用户未登录", Toast.LENGTH_LONG).show()
                navigationEvent.postValue(Event("MainActivity"))
            }
        }
    }

    fun overwriteCourse(context: Context) {
        tempCourseId?.let { courseId ->
            viewModelScope.launch {
                // 从SharedPreferences加载Token
                Log.d(TAG,"Attempting to overwriting course: $courseId")
                val (token, _) = loadCredentials(context)
                if (token != null) {
                    // 发送网络请求以覆盖课程
                    val response = makeCourseRequest(context, courseId, "overwrite_course_for_user", token)
                    when (response.first) {
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                            // 课程覆盖成功
                            Toast.makeText(context, "课程已覆盖", Toast.LENGTH_LONG).show()
                            navigationEvent.postValue(Event("MainActivity"))
                        }
                        else -> {
                            // 错误处理
                            Toast.makeText(context, "Error: ${response.second}", Toast.LENGTH_LONG).show()
                            navigationEvent.postValue(Event("MainActivity"))
                        }
                    }
                } else {
                    Toast.makeText(context, "用户未登录", Toast.LENGTH_LONG).show()
                    navigationEvent.postValue(Event("MainActivity"))
                }
            }
        }
    }

    private suspend fun makeCourseRequest(context: Context, courseId: String, apiPath: String, token: String): Pair<Int, String> = withContext(
        Dispatchers.IO) {
        val baseUrl = context.getString(R.string.server_base_url)
        val url = URL(baseUrl + apiPath)
        Log.d(TAG,"Make request:$url")

        var responsePair: Pair<Int, String> = 0 to "Initial response"

        (url.openConnection() as HttpURLConnection).apply {
            try {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token") // 添加Token到请求头

                // 构建请求体
                val requestJson = JSONObject().apply {
                    put("course_id", courseId)
                }
                Log.d(TAG,"Request body: $requestJson")
                outputStream.use { it.write(requestJson.toString().toByteArray()) }

                // 获取响应码
                val responseCode = responseCode
                Log.d(TAG, "Response code: $responseCode")

                // 处理响应
                val response = if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val responseBody = inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Response body: $responseBody")
                    responseBody // 成功响应
                } else {
                    val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    Log.e(TAG, "Error response body: $errorBody")
                    errorBody // 错误响应
                }

                responsePair = responseCode to response
            } catch (e: Exception) {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                e.printStackTrace(pw)
                val stackTraceString = sw.toString()
                Log.e(TAG, "Error during making course request: ${e.message}")
                Log.e(TAG,"Stack trace: $stackTraceString")
                responsePair = 0 to "Error during making course request: ${e.message}\nStack trace: $stackTraceString"
            } finally {
                disconnect()
            }
        }

        responsePair
    }


    private fun loadCredentials(context: Context): Pair<String?, Int> {
        val sharedPreferences = context.getSharedPreferences(context.getString(R.string.SharedPreferences), Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null)
        val userId = sharedPreferences.getInt("userId", -1)
        return Pair(token, userId)
    }

}

