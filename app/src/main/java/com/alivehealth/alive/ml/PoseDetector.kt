package com.alivehealth.alive.ml

import android.graphics.Bitmap
import com.alivehealth.alive.data.Person

/**
 * 定义了一个接口，estimatePoses是输入bitmap，输出用户17个关键点得分；lastInferenceTimeNanos是从计算开始持续的时长
 */
interface PoseDetector : AutoCloseable {

    fun estimatePoses(bitmap: Bitmap): List<Person>

    fun lastInferenceTimeNanos(): Long
}