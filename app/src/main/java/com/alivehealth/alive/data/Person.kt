package com.alivehealth.alive.data

import android.graphics.RectF


/**
 * 定义一个用户的数据类：用户ID，数据列表(身体姿态点，坐标，得分)，身体外框，整体得分
 *
 */
data class Person(
    var id: Int = -1, // default id is -1
    val keyPoints: List<KeyPoint>,//关于身体点，坐标和得分的列表
    val boundingBox: RectF? = null, // Only MoveNet MultiPose return bounding box.
    val score: Float
)