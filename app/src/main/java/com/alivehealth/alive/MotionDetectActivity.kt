package com.alivehealth.alive

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.alivehealth.alive.camera.CameraSource
import com.alivehealth.alive.data.Device
import com.alivehealth.alive.ml.ModelType
import com.alivehealth.alive.ml.MoveNet
import com.alivehealth.alive.ml.MoveNetMultiPose
import com.alivehealth.alive.ml.PoseClassifier
import com.alivehealth.alive.ml.PoseNet
import com.alivehealth.alive.ml.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import java.util.LinkedList
import java.util.Locale
import kotlin.math.roundToInt

class MotionDetectActivity : AppCompatActivity() {

    private val TAG="测试输出"

    // companion object 用于声明静态成员，类似于 Java 中的 static 字段。在这个例子中，它定义了一个字符串常量。
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** A [SurfaceView] for camera preview.   */
    private lateinit var surfaceView: SurfaceView

    /** Default pose estimation model is 1 (MoveNet Thunder)
     * 0 == MoveNet Lightning model
     * 1 == MoveNet Thunder model
     * 2 == MoveNet MultiPose model
     * 3 == PoseNet model
     **/
    private var modelPos = 1

    /** 设定默认CPU为处理单元 */
    private var device = Device.CPU

    // 以下各个 lateinit 变量是 UI 组件的声明，它们也会在之后被初始化。保留后续处理

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var scoreTextView: TextView
    private lateinit var poseCountTextView: TextView
    private lateinit var courseCode: String






    // cameraSource 可能为空，表示它可能没有被初始化。
    private var cameraSource: CameraSource? = null
    private var isClassifyPose = true

    //下面的变量用计算用户锻炼姿势监测结果
    private val poseSmoother = MovingAverage(10)
    private var poseStartTime: Long = 0
    private var poseLowScoreStartTime = 0L
    private var isPoseStandardMet = false
    private var isPoseCompleted = false
    private var currentElementIndex: Int = 0
    private var posePerformCount: Int = 0


    //课程信息的数据类
    data class Pose(val name: String, val count: Int)
    data class Rest(val duration: Long)
    data class Course(val id: String, val elements: List<Any>)//这个类用于存储一个完整课程的所有信息，包括课程的唯一标识和构成课程的各个元素（即姿势和休息时间）。

    private var course: Course? = null



    /**
     * 这段代码是与 Android 的新的权限请求模型相配合的，特别是在你的应用程序需要请求用户授权某个特定权限（
     * 如相机、位置等）时启用。
     * 在 Android 6.0（API 级别 23）及以上版本中，用户需要在运行时而不是在安装时授予权限。为
     * 了请求权限，应用程序需要在合适的时机（通常是在用户使用某项功能之前）向用户明确地请求权限。
     * 以下是触发此代码的典型情况：
     * 1. 用户操作： 用户可能点击了一个按钮来拍照，而拍照功能需要相机权限。在这种情况下，你的应用会调用
     * requestPermissionLauncher.launch(permission) 方法来请求权限，
     * 其中 permission 是一个字符串，如 Manifest.permission.CAMERA。
     * 2. 应用逻辑： 应用的流程可能需要某项权限才能继续。
     * 例如，在应用启动时，如果应用的主要功能需要访问相机，它可能会自动请求相机权限。
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
                openCamera()
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                ErrorDialog.newInstance(getString(R.string.tfe_pe_request_permission))
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
            }
        }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motiondetect)
        // keep screen on while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.surfaceView)
        scoreTextView = findViewById(R.id.scoreTextView)
        poseCountTextView = findViewById(R.id.poseCountTextView)

        courseCode = intent.getStringExtra("COURSE_CODE") ?: ""//接受从MainActivity传递过来的课程参数

        course = parseCourse(courseCode)


        Log.d(TAG,"Parsed pose: $course")

        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.CHINESE
            }
        }
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }

    }

    private fun parseCourse(courseCode: String): Course? {
        val parser = resources.getXml(R.xml.courses)
        var eventType = parser.eventType
        var currentCourse: Course? = null
        var currentElements: MutableList<Any>? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "course" -> {
                        val id = parser.getAttributeValue(null, "id")
                        if (id == courseCode) {
                            currentElements = mutableListOf()
                            currentCourse = Course(id, currentElements)
                        }
                    }
                    "pose" -> if (currentCourse != null) {
                        currentElements?.add(
                            Pose(
                                parser.getAttributeValue(null, "name"),
                                parser.getAttributeValue(null, "count").toInt()
                            )
                        )
                    }
                    "rest" -> if (currentCourse != null) {
                        currentElements?.add(
                            Rest(
                                parser.getAttributeValue(null, "duration").toLong()
                            )
                        )
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "course" && currentCourse != null) {
                    return currentCourse
                }
            }
            eventType = parser.next()
        }

        return null // 如果没有找到匹配的课程，返回 null
    }




    override fun onStart() {
        super.onStart()
        Log.d(TAG,"onStart启动")
        openCamera()
    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
    }

    override fun onDestroy() {
        textToSpeech.shutdown()
        super.onDestroy()
    }

    // check if permission is granted or not.
    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    // open camera
    private fun openCamera() {
        Log.d(TAG,"OpenCamera ON")
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource =
                    CameraSource(surfaceView, object : CameraSource.CameraSourceListener {
                        override fun onFPSListener(fps: Int) {
                            //tvFPS.text = getString(R.string.tfe_pe_tv_fps, fps)
                        }

                        override fun onDetectedInfo(
                            personScore: Float?,
                            poseLabels: List<Pair<String, Float>>?
                        ) {
                            Log.d(TAG,"onDetectedInfo ON")

                            //计数逻辑
                            val poseDuration = resources.getInteger(R.integer.pose_duration).toLong()
                            val poseExtendedDuration = resources.getInteger(R.integer.pose_extended_duration).toLong()
                            val poseThreshold = resources.getString(R.string.pose_threshold).toFloat()


                            //课程判断逻辑
                            fun performPose(pose: Pose) {
                                val poseScore =
                                    poseLabels?.find { it.first == pose.name }?.second ?: 0f
                                val poseTempName = pose.name
                                poseSmoother.add(poseScore)
                                val avgScore = poseSmoother.average()
                                val roundScore = (avgScore * 100).roundToInt()
                                scoreTextView.text = roundScore.toString()


                                speak(getString(R.string.course_start))
                                if (posePerformCount < pose.count) {

                                    if (avgScore >= poseThreshold && !isPoseStandardMet) {
                                        poseStartTime = System.currentTimeMillis()
                                        isPoseStandardMet = true
                                        isPoseCompleted = false
                                        speak(getString(R.string.pose_standard_met))
                                    } else if (isPoseStandardMet) {
                                        val elapsedTime = System.currentTimeMillis() - poseStartTime
                                        if (elapsedTime >= poseDuration && !isPoseCompleted) {
                                            isPoseCompleted = true
                                            posePerformCount++
                                            poseCountTextView.text = posePerformCount.toString()
                                            speak("完成了 $posePerformCount 次")

                                        } else if (avgScore < poseThreshold) {
                                            if (poseLowScoreStartTime == 0L) {
                                                poseLowScoreStartTime = System.currentTimeMillis()
                                            }

                                            if (System.currentTimeMillis() - poseLowScoreStartTime >= poseExtendedDuration) {
                                                isPoseStandardMet = false
                                                isPoseStandardMet = false
                                                speak(getString(R.string.pose_restart))
                                            }
                                        } else {
                                            poseLowScoreStartTime = 0L
                                        }
                                    }
                                } else {
                                    speak(getString(R.string.breakMSG))

                                    currentElementIndex++
                                    posePerformCount = 0
                                    Log.d(TAG,"course finished,index:$currentElementIndex")
                                }
                            }

                            fun performRest(rest: Rest) {
                                lifecycleScope.launch {
                                    val totalDurationInSeconds = rest.duration / 1000
                                    for (time in totalDurationInSeconds.toInt() downTo 1) {
                                        scoreTextView.text = "休息: $time 秒"
                                        delay(1000) // 每次循环延迟1秒
                                    }
                                    scoreTextView.text = "休息结束"
                                    currentElementIndex++  // 休息结束后，更新课程进度
                                    Log.d(TAG,"rest ,index:$currentElementIndex ")
                                }
                            }


                            fun onCourseCompleted(){
                                Log.d(TAG,"Course ended,index:$currentElementIndex")
                                scoreTextView.text = "课程结束"

                            }

                            fun startCourse(course: Course?) {
                                Log.d(TAG,"Start Course,index:$currentElementIndex")
                                course?.elements?.let { elements ->
                                    if (currentElementIndex < elements.size) {
                                        Log.d(TAG, elements.size.toString())
                                        when (val element = elements[currentElementIndex]) {
                                            is Pose ->
                                            {
                                                performPose(element)
                                                Log.d(TAG,"Pose-> element: $element")
                                                Log.d(TAG,"index:$currentElementIndex")
                                            }
                                            is Rest ->
                                            {
                                                performRest(element)
                                                Log.d(TAG,"Rest -> element:$element")
                                                Log.d(TAG,"index:$currentElementIndex")
                                            }
                                            else -> {Log.d(TAG,"course have No pose or rest")}
                                        }
                                    } else {
                                        onCourseCompleted()
                                        Log.d(TAG,"course complete")
                                    }
                                }
                            }

                            startCourse(course)
                            Log.d(TAG,"Start course")
                        }
                    }).apply {
                        prepareCamera()
                        Log.d(TAG,"prepareCamera")
                    }
                isPoseClassifier()
                Log.d(TAG,"isPoseClassifier")
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                    Log.d(TAG,"camera init")
                }
            }
            createPoseEstimator()
            Log.d(TAG,"PoseEstimator")
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





    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }


    private fun convertPoseLabels(pair: Pair<String, Float>?): String {
        if (pair == null) return "empty"
        return "${pair.first} (${String.format("%.2f", pair.second)})"
    }

    private fun isPoseClassifier() {
        cameraSource?.setClassifier(if (isClassifyPose) PoseClassifier.create(this) else null)
    }


    /**
     * 这段代码的目的是根据条件选择合适的姿势检测器并配置其行为。最后，如果检测器和摄像头源都不为 null，
     * 它会将这个检测器设置为摄像头源的当前检测器。这样做的好处是它避免了在处理可空对象时可能出现的
     * NullPointerException。
     */
    private fun createPoseEstimator() {
        // For MoveNet MultiPose, hide score and disable pose classifier as the model returns
        // multiple Person instances.
        val poseDetector = when (modelPos) {
            0 -> {
                // MoveNet Lightning (SinglePose)
                //showPoseClassifier(true)
                //showDetectionScore(true)
                //showTracker(false)
                MoveNet.create(this, device, ModelType.Lightning)
            }
            1 -> {
                // MoveNet Thunder (SinglePose)
                //showPoseClassifier(true)
                //showDetectionScore(true)
                //showTracker(false)
                MoveNet.create(this, device, ModelType.Thunder)
            }
            2 -> {
                // MoveNet (Lightning) MultiPose
                //showPoseClassifier(false)
                //showDetectionScore(false)
                // Movenet MultiPose Dynamic does not support GPUDelegate
                //if (device == Device.GPU) {
                    //showToast(getString(R.string.tfe_pe_gpu_error))
                //}
                //showTracker(true)
                MoveNetMultiPose.create(
                    this,
                    device,
                    Type.Dynamic
                )
            }
            3 -> {
                // PoseNet (SinglePose)
                //showPoseClassifier(true)
                //showDetectionScore(true)
                //showTracker(false)
                PoseNet.create(this, device)
            }
            else -> {
                null
            }
        }
        /*
        Lambda 表达式：在这个表达式中，detector 是 poseDetector 的非空版本。然后再次使用安全调用操作符 ?.
        来检查 cameraSource 是否为 null。如果不是，那么执行 cameraSource.setDetector(detector)，
        将姿势检测器设置为摄像头源的检测器。
         */
        poseDetector?.let { detector ->
            cameraSource?.setDetector(detector)
        }
    }


    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                // You can use the API that requires the permission.
                openCamera()
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // do nothing
                }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }

}