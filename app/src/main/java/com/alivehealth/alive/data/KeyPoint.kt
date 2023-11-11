package com.alivehealth.alive.data
import android.graphics.PointF

/**
 * 定义关键点，身体部分，坐标(XY)，判断得分
 */
data class KeyPoint(val bodyPart: BodyPart, var coordinate: PointF, val score: Float)
