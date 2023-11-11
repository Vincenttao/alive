package com.alivehealth.alive

import android.os.Bundle
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.alivehealth.alive.data.Device

class MotionDetectActivity : AppCompatActivity() {

    // companion object 用于声明静态成员，类似于 Java 中的 static 字段。在这个例子中，它定义了一个字符串常量。
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** Default pose estimation model is 1 (MoveNet Thunder)
     * 0 == MoveNet Lightning model
     * 1 == MoveNet Thunder model
     * 2 == MoveNet MultiPose model
     * 3 == PoseNet model
     **/
    private var modelPos = 0

    /** 设定默认CPU为处理单元 */
    private var device = Device.CPU

    // 以下各个 lateinit 变量是 UI 组件的声明，它们也会在之后被初始化。保留后续处理
    // private lateinit var tvScore: TextView
    // private lateinit var tvFPS: TextView


    private lateinit var surfaceView: SurfaceView

    //private var cameraSource: CameraSource? = null
    private var isClassifyPose = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motiondetect)
    }


}