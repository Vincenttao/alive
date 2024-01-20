package com.alivehealth.alive.motiondetectlogic

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alivehealth.alive.data.PoseDetectionLogic
import java.util.LinkedList
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
    private val activityStartTime: Long,
    private val targetCount: Int = 1,
    private val poseThreshold: Float = 0.7f,
    private val poseDurationThreshold: Long,
    private val poseExtendedDuration: Long = 1000L,//毫秒
    private val detectionLogic: PoseDetectionLogic,
    private val updateUI: (progressBarProgress:Int, currentPose:String, textInMiddle:String, progressBarMax:Int) -> Unit // UI更新函数
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








    private fun finishPoseDetection() {
        Log.d(TAG, "finishPoseDetection: Pose detection finished.")
        // 按需执行完成动作，例如更新 UI，通知用户等
        // 调用 Activity 的方法来结束 Activity 并返回结果
        val averagePoseScore = if (scoreCount > 0) totalScore / scoreCount else 0f
        val durationTime = ((System.currentTimeMillis()-activityStartTime) / 1000).toInt()
        val finishTime = System.currentTimeMillis()

        val returnIntent = Intent().apply {
            putExtra("averagePoseScore", averagePoseScore)
            putExtra("durationTime", durationTime)
            putExtra("finishTime", finishTime)
        }

        (context as? Activity)?.apply {
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
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