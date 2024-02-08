package com.alivehealth.alive.motiondetectlogic

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getString
import com.alivehealth.alive.MainActivity
import com.alivehealth.alive.R
import com.alivehealth.alive.data.PoseDetectionLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import kotlin.math.roundToInt



/*
`PoseDetectionManager` 类用于管理姿势检测的逻辑。它包含以下主要参数和功能：

- `context`: 上下文，通常是当前活动的引用。
- `activityStartTime`: 活动开始时间，用于计算活动持续时间。
- `targetCount`: 目标完成次数。
- `poseThreshold`: 姿势检测的阈值。
- `poseDuration`: 姿势持续时间。
- `poseExtendedDuration`: 姿势低于阈值的容忍时间。
- `isTimeBased`: 是否基于时间来判断完成。
- `updateUI`: 更新用户界面的函数。

主要函数：

- `updatePoseDetection`: 根据当前姿势和评分更新检测逻辑。
- `handlePoseScoring`: 处理姿势评分逻辑。
- `handlePoseAboveThreshold` 和 `handlePoseBelowThreshold`: 处理姿势评分是否达到阈值的情况。
- `finishPoseDetection`: 完成姿势检测，计算总平均得分和持续时间，关闭当前活动并返回结果。

该类通过评估用户的姿势是否满足设定的阈值和时长来判断是否完成特定的动作或姿势。
 */
class PoseDetectionManager(
    private val context: Context,
    private val exerciseId: Int,
    private val activityStartTime: Long,
    private val targetCount: Int = 1,
    private val poseThreshold: Float = 0.7f,
    private val poseDurationThreshold: Long,
    private val poseExtendedDuration: Long = 1000L,//毫秒
    private val detectionLogic: PoseDetectionLogic,
    private val updateUI: (progressBarProgress:Int, currentPose:String, textInMiddle:String, progressBarMax:Int) -> Unit
//todo 修改UI更新逻辑，以圆环颜色来区别是否达标
) {
    private var posePerformCount = 0
    private var poseStartTime: Long = 0L
    private var poseLowScoreStartTime: Long = 0L
    private var poseDurationTime: Long = 0L
    private var isPoseStandardMet = false
    private var isPoseCompleted = false
    private val poseSmoother = MovingAverage(10)
    private var totalScore = 0f
    private var scoreCount = 0
    private var frameCount = 0
    private val TAG = "PoseDetectionManager 测试"

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var isUploaded = false



    fun updatePoseDetection(poseLabels: List<Pair<String, Float>>, currentPose: String) {
        frameCount++
        if ((frameCount == 0) or (frameCount % 10 == 0)){
            Log.d(TAG, "updatePoseDetection: Updating pose detection.")
            Log.d(TAG,"Pose detection result:$poseLabels")
        }
        val poseScore = poseLabels.find { it.first == currentPose }?.second ?: 0f
        poseSmoother.add(poseScore)
        val avgScore = poseSmoother.average()
        val roundScore = (avgScore * 100).roundToInt()

        when (detectionLogic) {
            PoseDetectionLogic.COUNT_ABOVE_THRESHOLD -> updateForCountAboveThreshold(roundScore, currentPose, avgScore)
            PoseDetectionLogic.TIME_ABOVE_THRESHOLD -> updateForTimeAboveThreshold(roundScore, currentPose, avgScore)
            PoseDetectionLogic.PURE_TIME -> updateForPureTime(currentPose)
        }
    }

    private fun updateForPureTime(currentPose: String) {
        val elapsedTime = System.currentTimeMillis() - activityStartTime
        updateUI(0, currentPose, (elapsedTime/1000).toString(),100)
        if (elapsedTime >= poseDurationThreshold) finishPoseDetection()
    }

    private fun updateForTimeAboveThreshold(roundScore: Int, currentPose: String, avgScore: Float) {
        updateUI(roundScore,currentPose, (poseDurationTime / 1000).toString(),100)

        fun handlePoseBelowThreshold(){
            if (isPoseStandardMet){
                if (poseLowScoreStartTime == 0L) {
                    poseLowScoreStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - poseLowScoreStartTime >= poseExtendedDuration){
                    isPoseStandardMet = false
                    poseLowScoreStartTime = 0L
                    }
            }
        }

        fun handlePoseAboveThreshold(){
            if (isPoseStandardMet){
                poseDurationTime = System.currentTimeMillis() - poseStartTime
            } else {
                isPoseStandardMet = true
                poseStartTime = System.currentTimeMillis()
            }
        }
        fun handlePoseScoring(avgScore: Float){
            if(frameCount % 100 ==0){
                Log.d(TAG, "handlePoseScoring: Handling pose scoring with avgScore: $avgScore")
            }
            if(avgScore >= poseThreshold){
                totalScore += avgScore
                scoreCount++
                handlePoseAboveThreshold()
            } else {
                handlePoseBelowThreshold()
            }
        }


        if (poseDurationTime < poseDurationThreshold){
            handlePoseScoring(avgScore)
        } else {
            finishPoseDetection()
        }

    }


    //todo 不能简单显示一个数字，需要有待完成n个动作，完成x个动作，还有提示用户坚持多长时间
    private fun updateForCountAboveThreshold(roundScore: Int, currentPose: String, avgScore: Float) {
        updateUI(roundScore,currentPose,posePerformCount.toString(),100)
        fun handlePoseAboveThreshold(){
            if (isPoseStandardMet) {
                if (System.currentTimeMillis() - poseStartTime >= poseDurationThreshold) {
                    posePerformCount++
                    isPoseStandardMet = false
                    isPoseCompleted = true
                }
            } else {
                isPoseStandardMet = true
                poseStartTime = System.currentTimeMillis()
            }
        }

        fun handlePoseBelowThreshold(){
            if (isPoseStandardMet) {
                if (poseLowScoreStartTime == 0L) {
                    poseLowScoreStartTime = System.currentTimeMillis()
                } else if(System.currentTimeMillis() - poseLowScoreStartTime >= poseExtendedDuration){
                    isPoseStandardMet = false
                    isPoseCompleted = false
                    poseLowScoreStartTime = 0L
                }
            }
        }

        fun handlePoseScoring(avgScore:Float){
            if(frameCount % 100 ==0){
                Log.d(TAG, "handlePoseScoring: Handling pose scoring with avgScore: $avgScore")
            }
            if (!isPoseCompleted) {
                if (avgScore >= poseThreshold){
                    totalScore += avgScore
                    scoreCount++
                    handlePoseAboveThreshold()
                } else {
                    handlePoseBelowThreshold()
                }

            } else if(avgScore < poseThreshold){
                isPoseCompleted = false
            }
        }

        if (posePerformCount < targetCount) handlePoseScoring(avgScore)
        else finishPoseDetection()
    }







//TODO 需要增加一个没有完成机制

    fun finishPoseDetection() {
        if (isUploaded) {
            return //如果已经上传过，则不再执行
        }

        isUploaded = true //设置标志为已上传

        Log.d(TAG, "finishPoseDetection: Pose detection finished.")

        // 按需执行完成动作，例如更新 UI，通知用户等
        // 调用 Activity 的方法来结束 Activity 并返回结果
        coroutineScope.launch {
            val sharedPreferences = context.getSharedPreferences(context.getString(R.string.SharedPreferences), Context.MODE_PRIVATE)
            val token = sharedPreferences.getString("token","")
            if(token.isNullOrEmpty()){
                Log.e(TAG,"Token not found")
                //return@launch
            }
            val averagePoseScore = if (scoreCount > 0) (totalScore * 100.0f)/scoreCount else 0f
            val durationTime = ((System.currentTimeMillis() - activityStartTime) / 1000).toInt()
            val dateCompleted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(Date())

            try {
                val (responseCode, responseMessage) = uploadExerciseData(
                    context,
                    exerciseId = exerciseId,
                    dateCompleted,
                    averagePoseScore.roundToInt(),
                    durationTime,
                    completed = true,
                    userToken= token.toString())
                Log.d(TAG,"Upload response: $responseCode, message: $responseMessage")
            } catch (e:Exception) {
                Log.e(TAG,"Error uploading exercise history data: ${e.message}")
            }

            // 结束上传后直接返回MainActivity并清除之前的活动栈
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            withContext(Dispatchers.Main) {
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }


        }

    }

    private suspend fun uploadExerciseData(
        context: Context,
        exerciseId: Int,
        dateCompleted: String,
        score: Int,
        durationTime: Int,//秒
        completed: Boolean,
        userToken: String // 添加一个用于身份验证的token参数
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val baseUrl = context.getString(R.string.server_base_url) // 你的服务器基地址
        val url = URL("$baseUrl/add_exercise_history")

        var responsePair: Pair<Int, String> = 0 to "Initial response"

        (url.openConnection() as HttpURLConnection).apply {
            try {
                requestMethod = "POST"
                doOutput = true // 允许向服务器输出
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $userToken") // 携带用户的token

                // 构建 JSON 数据
                val json = JSONObject().apply {
                    put("exercise_id", exerciseId)
                    put("date_completed", dateCompleted)
                    put("score", score)
                    put("durationtime", durationTime)
                    put("completed", completed)
                }

                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }

                // 处理响应
                val responseCode = responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    inputStream.bufferedReader().use { it.readText() }
                } else {
                    errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                }

                responsePair = responseCode to response
            } catch (e: Exception) {
                Log.e("HTTP_POST", "Error during uploading exercise data: ${e.message}")
                responsePair = 0 to "Error during uploading exercise data: ${e.message}"
            } finally {
                disconnect()
            }
        }

        responsePair
    }



}

class MovingAverage(private val size: Int) {
    private val values = LinkedList<Float>()
    fun add(value: Float) {
        if (values.size == size) {
            values.poll()
        }
        values.add(value)
    }
    fun average(): Float {
        return if (values.isEmpty()) 0f else values.sum() / values.size
    }
}