package com.alivehealth.alive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "测试 ExerciseActivity"

data class Exercise(
    val id: Int, 
    val name: String,
    val name_en: String,
    val intensity: String?,
    val types: String?,
    val posture: String?,
    val key_points: String?,
    val precautions: String?,
    val duration: Int?,
    val repetitions: Int?,
    val sides: String?,
    val focus_area: String?,
    val logic: String?,
    val pose_classifier: String?
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

    private lateinit var videoViewExercise: VideoView
    private lateinit var tvExerciseName: TextView
    private lateinit var tvExerciseKP: TextView
    private lateinit var tvExerciseIntensity: TextView
    private lateinit var tvExercisePosture: TextView
    private lateinit var tvExerciseRepetitions: TextView
    private lateinit var tvExerciseDuration: TextView
    private lateinit var tvExercisePrecautions: TextView
    private lateinit var startExerciseButton: MaterialButton

    private lateinit var  currentExercise: Exercise

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.exercise)

        videoViewExercise = findViewById(R.id.videoViewExercise)
        tvExerciseName = findViewById(R.id.tvExerciseName)
        tvExerciseKP = findViewById(R.id.tvExerciseKP)
        tvExerciseIntensity = findViewById(R.id.tvExerciseIntensity)
        tvExercisePosture = findViewById(R.id.tvExercisePosture)
        tvExerciseRepetitions = findViewById(R.id.tvExerciseRepetitions)
        tvExerciseDuration = findViewById(R.id.tvExerciseDuration)
        tvExercisePrecautions = findViewById(R.id.tvExercisePrecautions)

        startExerciseButton = findViewById(R.id.startExerciseButton)

        val exerciseId = intent.getIntExtra("exerciseId", 0)

        loadExerciseDetails(exerciseId)


        startExerciseButton.setOnClickListener{
            startMotionDetectActivity()
        }



    }

    private fun startMotionDetectActivity() {
        val intent = Intent(this, MotionDetectActivity::class.java)

        intent.putExtra("name", currentExercise.name_en)
        intent.putExtra("count", currentExercise.repetitions ?:1)
        intent.putExtra("poseDuration", currentExercise.duration ?:1000)
        intent.putExtra("logic", currentExercise.logic)
        intent.putExtra("poseClassifier", currentExercise.pose_classifier)

        startActivity(intent)
    }

    private fun updateUI(exercise: Exercise) {
        currentExercise = exercise
        tvExerciseName.text = exercise.name
        tvExerciseIntensity.text = "锻炼强度：" + (exercise.intensity?:"无")
        tvExercisePosture.text = "姿势："+ (exercise.posture?:"无")
        tvExerciseRepetitions.text = "重复次数：" + (exercise.repetitions?:"0") + "次"
        tvExerciseDuration.text = "持续时间：" + (exercise.duration?:"0") + "秒"
        tvExerciseKP.text = "关键点：\n" + (exercise.key_points?:"无")
        tvExercisePrecautions.text = "注意点：\n" + (exercise.precautions?:"无")


        val videoResId = getRawResIdByName(exercise.name_en.lowercase())
        val rawVideoUri = "android.resource://${packageName}/$videoResId"
        videoViewExercise.setVideoPath(rawVideoUri)
        // 设置视频完成后的监听器，用于循环播放
        videoViewExercise.setOnCompletionListener {
            // 视频播放完毕时重新开始播放
            videoViewExercise.start()
        }
        videoViewExercise.start()
    }

    private fun loadExerciseDetails(exerciseId: Int) {
        // 使用协程异步获取练习详情
        GlobalScope.launch(Dispatchers.IO) {
            val (responseCode, responseString) = fetchExerciseDetails(this@ExerciseActivity, exerciseId)
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val exercise = parseExerciseJson(responseString)
                exercise?.let {
                    withContext(Dispatchers.Main) {
                        updateUI(it)
                    }
                }
            }
        }
    }

    private fun getRawResIdByName(resName: String): Int {
        return try {
            val res = R.raw::class.java
            val field = res.getField(resName)
            field.getInt(null)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

}