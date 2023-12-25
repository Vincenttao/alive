package com.alivehealth.alive

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "测试 ExerciseActivity"

data class Exercise(
    val id: Int,
    val name: String,
    val intensity: String?,
    val types: String?,
    val posture: String?,
    val key_points: String?,
    val precautions: String?,
    val duration: Int?,
    val repetitions: Int?,
    val sides: String?,
    val focus_area: String?
)

val sampleExercise = Exercise(
    id = 1,
    name = "伸展胸背部",
    intensity = "轻度",
    types = "伸展",
    posture = "坐位",
    key_points = "- 双臂向外抬举，感觉胸部抬高 - 伸展胸部、背部肌肉 - 深吸一口气，缓慢放松吐气 ",
    precautions = " 如果您存在肩背部疼痛，注意活动不要加重症状 ",
    duration = 20,
    repetitions = 0,
    sides = "none",
    focus_area = "胸部"
)


private suspend fun fetchExerciseDetails(context: Context, exerciseId: Int): Pair<Int, String> = withContext(
    Dispatchers.IO) {
    val baseUrl = context.getString(R.string.server_base_url)
    val url = URL(baseUrl+"exercise/"+exerciseId)
    Log.d(TAG, "Make request: $url")

    var responsePair: Pair<Int, String> = 0 to "Initial response"

    (url.openConnection() as HttpURLConnection).apply {
        try {
            requestMethod = "GET"

            // 获取响应码
            val responseCode = responseCode
            Log.d(TAG, "Response code: $responseCode")

            // 处理响应
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream.bufferedReader().use { it.readText() }
            } else {
                errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }

            responsePair = responseCode to response
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTraceString = sw.toString()
            Log.e(TAG, "Error during fetching exercise details: ${e.message}")
            responsePair = 0 to "Error during fetching exercise details: ${e.message}\nStack trace: $stackTraceString"
        } finally {
            disconnect()
        }
    }

    responsePair
}

private fun parseExerciseJson(jsonString: String): Exercise? {
    return try {
        val gson = Gson()
        gson.fromJson(jsonString, Exercise::class.java)
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing exercise JSON with Gson: ${e.message}")
        null
    }
}

class ExerciseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val exerciseId = intent.getIntExtra("exerciseId", 0)

        setContent {
            MaterialTheme {
                ExerciseScreen(exerciseId = exerciseId)
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun ExerciseScreenPreview() {
        setContent {
            MaterialTheme{
                ExerciseDetails(exercise = sampleExercise)
            }
        }
    }

    @Composable
    fun ExerciseScreen(exerciseId: Int) {
        var exercise by remember { mutableStateOf<Exercise?>(null) }
        val context = LocalContext.current

        // 在 Compose 生命周期内启动协程
        LaunchedEffect(key1 = exerciseId) {
            val (responseCode, responseString) = fetchExerciseDetails(this@ExerciseActivity,exerciseId)
            if (responseCode == HttpURLConnection.HTTP_OK) {
                exercise = parseExerciseJson(responseString)
            }
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { /* 启动锻炼的逻辑 */
                    Toast.makeText(context, "启动锻炼", Toast.LENGTH_SHORT).show()
                }) {
                    Text("启动",fontSize = 24.sp)
                    //Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start Exercise")
                }
            }
        ) {paddingValues -> //接收Scaffold的contentPadding
            // Surface 和 Box 的内容
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    contentAlignment = Alignment.Center) {
                    if (exercise != null) {
                        ExerciseDetails(exercise = exercise!!)
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    @Composable
    fun ExerciseDetails(exercise: Exercise) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            Log.d(TAG,"exercise: $exercise")
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Image(painter = painterResource(id = R.drawable.exercise1),
                contentDescription = "Exercise 1 Image"
                )
            exercise.intensity?.let { DetailCard("Intensity", it) }
            exercise.types?.let { DetailCard("Type", it) }
            exercise.posture?.let { DetailCard("Posture", it) }
            exercise.key_points?.let { DetailCard("Key Points", it) }
            exercise.precautions?.let { DetailCard("Precautions", it) }
            if ((exercise.duration ?: 0) > 0) {
                DetailCard("Duration", "${exercise.duration} seconds")
            }
            if ((exercise.repetitions ?: 0) > 0) {
                DetailCard("Repetitions", exercise.repetitions.toString())
            }
            exercise.sides?.let { DetailCard("Sides", it) }
            exercise.focus_area?.let { DetailCard("Focus Area", it) }
        }
    }

    @Composable
    fun DetailCard(label: String, value: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}