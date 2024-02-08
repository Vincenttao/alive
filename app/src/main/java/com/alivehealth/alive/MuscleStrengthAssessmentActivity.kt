package com.alivehealth.alive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

private val TAG = "MuscleStrength测试"

data class MSAQuestionItem(
    val id: Int,
    val text: String,
    val options: List<MSAOption>,
    val imageResName: String
)

data class MSAOption(
    val text: String,
    val score: Int
)

data class MSACourse(
    val courseId: String,
    val courseName: String
)


class MSAQuestionViewModel : ViewModel() {

    private val questions = listOf(
        MSAQuestionItem(
            id = 1,
            text = "举起或搬动4.5kg物体是否存在困难",
            imageResName = "lift_object",
            options = listOf(
                MSAOption("没有困难", 0),
                MSAOption("稍有困难", 1),
                MSAOption("困难较大，需要辅助器具，需要他人帮助", 2)
            )
        ),
        MSAQuestionItem(
            id = 2,
            text = "步行穿过房间是否存在困难，是否需要帮助？",
            imageResName = "walking_across",
            options = listOf(
                MSAOption("没有困难", 0),
                MSAOption("稍有困难", 1),
                MSAOption("困难较大，需要辅助器具，需要他人帮助", 2)
            )
        ),
        MSAQuestionItem(
            id = 3,
            text = "从椅子/床起立是否存在困难，是否需要帮助？",
            imageResName = "rise_from_chair",
            options = listOf(
                MSAOption("没有困难", 0),
                MSAOption("稍有困难", 1),
                MSAOption("困难较大，需要辅助器具，需要他人帮助", 2)
            )
        ),
        MSAQuestionItem(
            id = 4,
            text = "爬10层台阶是否存在困难？",
            imageResName = "climb_staircase",
            options = listOf(
                MSAOption("没有困难", 0),
                MSAOption("稍有困难", 1),
                MSAOption("困难较大或不能完成", 2)
            )
        ),
        MSAQuestionItem(
            id = 5,
            text = "过去一年是否有跌倒情况？",
            imageResName = "momentary",
            options = listOf(
                MSAOption("过去一年没有跌倒史", 0),
                MSAOption("过去一年跌倒1-3次", 1),
                MSAOption("过去一年跌倒4次以上", 2)
            )
        ),
        // 添加其他问题...
    )
    private val _navigationEvent = MutableLiveData<Class<*>>()
    val navigationEvent: LiveData<Class<*>> = _navigationEvent

    private val _navigateToMainActivityEvent = MutableLiveData<Boolean>()
    val navigateToMainActivityEvent: LiveData<Boolean> = _navigateToMainActivityEvent
    fun triggerNavigateToMainActivity() {
        _navigateToMainActivityEvent.value = true
    }


    var showDialog by mutableStateOf(false)
    private var tempCourseId: String? = null
    private fun showOverwriteDialog(courseId: String) {
        showDialog = true
        tempCourseId = courseId
    }



    var courseData = listOf(
        MSACourse(courseId = "7", courseName = "基础套餐"),
        MSACourse(courseId = "8", courseName = "进阶套餐"),
        MSACourse(courseId = "9", courseName = "强化套餐")
    )


    private var totalScore = 0
    var currentQuestionIndex = 0
    var currentQuestion by mutableStateOf(questions.firstOrNull())
    var recommendation by mutableStateOf<String?>(null)
        private set

    fun onOptionSelected(score: Int) {
        totalScore += score
        if (currentQuestionIndex < questions.size - 1) {
            currentQuestionIndex++
            currentQuestion = questions[currentQuestionIndex]
        } else {
            currentQuestion = null // 问卷结束
            Log.d(TAG,"TotalScore:$totalScore")
            recommendExerciseBasedOnScore(totalScore)
        }
    }

    private fun recommendExerciseBasedOnScore(score: Int) {
        recommendation = when {
            score > 4 -> "7"
            score in 1..3 -> "8"
            else -> "9"
        }
        // 处理推荐逻辑，例如显示对话框或更新 UI
    }

    fun addToList(context: Context, courseId: String) {
        Log.d(TAG, "addToList function is called")
        viewModelScope.launch {
            Log.d(TAG, "Inside viewModelScope.launch")
            val (token, _) = loadCredentials(context)
            if (token != null) {
                val response = makeCourseRequest(context, courseId, "add_course_to_user", token)
                when (response.first) {
                    HttpURLConnection.HTTP_OK,HttpURLConnection.HTTP_CREATED ->{
                        Toast.makeText(context, "成功添加", Toast.LENGTH_LONG).show()
                        _navigationEvent.postValue(MainActivity::class.java) // 更新为导航事件
                    }
                    HttpURLConnection.HTTP_CONFLICT -> {
                        showOverwriteDialog(courseId)
                    }
                    else -> {
                        Toast.makeText(context, "Error: ${response.second}", Toast.LENGTH_LONG).show()
                        _navigationEvent.postValue(MainActivity::class.java) // 更新为导航事件
                    }
                }
            } else {
                Toast.makeText(context, "用户未登录", Toast.LENGTH_LONG).show()
                _navigationEvent.postValue(MainActivity::class.java) // 更新为导航事件
            }
        }
    }

    fun overwriteCourse(context: Context){
        tempCourseId?.let { courseId ->
            viewModelScope.launch {
                val (token, _) = loadCredentials(context)
                if (token != null) {
                    val response = makeCourseRequest(context, courseId, "overwrite_course_for_user", token)
                    //handleCourseResponse(context, response)
                    when (response.first) {
                        HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                            // 课程覆盖成功
                            Toast.makeText(context, "课程已覆盖", Toast.LENGTH_LONG).show()
                            _navigationEvent.postValue(MainActivity::class.java) // 更新为导航事件
                        }
                        else -> {
                            // 错误处理
                            Toast.makeText(context, "Error: ${response.second}", Toast.LENGTH_LONG).show()
                            _navigationEvent.postValue(MainActivity::class.java) // 更新为导航事件
                        }
                    }
                } else {
                    Toast.makeText(context, "用户未登录", Toast.LENGTH_LONG).show()
                    _navigationEvent.postValue(MainActivity::class.java) // 更新为导航事件
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

    fun getCourseNameById(courseId: String): String? {
        return courseData.find { it.courseId == courseId }?.courseName
    }

    fun onNavigateToMainActivityDone() {
        _navigateToMainActivityEvent.value = false
    }

}



class MuscleStrengthAssessmentActivity : ComponentActivity() {
    override  fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)

        setContent {
            MuscleStrengthAssessmentScreen(activity = this@MuscleStrengthAssessmentActivity)
        }

        val viewModel: MSAQuestionViewModel by viewModels()
        viewModel.navigationEvent.observe(this) { targetActivityClass ->
            targetActivityClass?.let {
                startActivity(Intent(this, it))
            }
        }

        viewModel.navigateToMainActivityEvent.observe(this) { shouldNavigate ->
            if (shouldNavigate) {
                navigateToMainActivity()
                viewModel.onNavigateToMainActivityDone() // 重置事件状态
            }

        }


    }

    private fun navigateToMainActivity(){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

}


@Composable
fun MuscleStrengthAssessmentScreen(viewModel: MSAQuestionViewModel = viewModel(),activity: Context){
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
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "肌肉力量评估",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(20.dp))


            viewModel.currentQuestion?.let { question ->
                MSAQuestionCard(question = question, onOptionSelected = { score ->
                    viewModel.onOptionSelected(score)
                })
            }?:run{
                // Assuming you handle the recommendation in the ViewModel
                val recommendation = viewModel.recommendation

                if(recommendation != null) {
                    val courseId = recommendation
                    val courseName = viewModel.getCourseNameById(recommendation)
                    Log.d(TAG, "检测结束。推荐课程：${recommendation ?: "无"}$courseName")


                    ConfirmAddToListDialog(
                        courseId = courseId ?: "",
                        courseName = courseName ?: "",
                        onConfirm = { newCourseId ->
                            viewModel.addToList(activity,newCourseId)
                        },
                        onCancel = {
                            viewModel.triggerNavigateToMainActivity()
                        }
                    )
                }else {
                    Text("检测结束。推荐课程:无")
                }



            }

            if(viewModel.showDialog) {
                OverwriteCourseDialog(viewModel = viewModel, activityContext = activity)
            }


        }
    }


}


@Composable
fun MSAQuestionCard(question: MSAQuestionItem, onOptionSelected: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Assuming each question might have an image representation
            // Image representation is optional. Remove if not applicable.
            question.imageResName?.let { imageName ->
                val imageResId = LocalContext.current.resources.getIdentifier(imageName, "drawable", LocalContext.current.packageName)
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = "Question Image",
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            question.options.forEach { option ->
                Button(
                    onClick = { onOptionSelected(option.score) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(option.text)
                }
            }
        }
    }
}





@Composable
fun ConfirmAddToListDialog(
    courseId: String,
    courseName: String,
    onConfirm: (String) -> Unit,
    onCancel:() -> Unit
) {
    // 使用对话框组件询问用户
    AlertDialog(
        onDismissRequest = {/*话题关闭时处理方法*/},
        title = { Text("添加$courseName 课程") },
        text = { Text("是否要将课程 $courseName 添加到您的每日清单中？") },
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
fun OverwriteCourseDialog(viewModel: MSAQuestionViewModel, activityContext: Context) {
    Log.d(TAG,"Overwrite dialog displaying")
    AlertDialog(
        onDismissRequest = { viewModel.showDialog = false },
        title = { Text("覆盖课程") },
        text = { Text("此课程已在您的清单中。是否要覆盖它？") },
        confirmButton = {
            Button(onClick = {
                Log.d(TAG,"onClick")
                viewModel.overwriteCourse(activityContext)
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