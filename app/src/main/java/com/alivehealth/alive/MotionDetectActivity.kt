package com.alivehealth.alive

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
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
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class MotionDetectActivity : AppCompatActivity() {

    private val TAG="MotionDetect+测试"

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
    private lateinit var poseNameTextView: TextView
    private lateinit var scoreTextView: TextView
    private lateinit var poseCountTextView: TextView



    private lateinit var pose: String
    private var count = 0

    private var poseDuration = 2000L
    private var poseExtendedDuration = 1000L
    private var poseThreshold = 0.8


    // cameraSource 可能为空，表示它可能没有被初始化。
    private var cameraSource: CameraSource? = null
    private var isClassifyPose = true

    //下面的变量用计算用户锻炼姿势监测结果
    private val poseSmoother = MovingAverage(10)
    private var poseStartTime: Long = 0L
    private var poseLowScoreStartTime = 0L
    private var isPoseStandardMet = false
    private var isPoseCompleted = false
    private var posePerformCount: Int = 0


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
        poseNameTextView = findViewById(R.id.poseNameTextView)
        scoreTextView = findViewById(R.id.scoreTextView)
        poseCountTextView = findViewById(R.id.poseCountTextView)

        pose = intent.getStringExtra("name") ?:""
        count = intent.getIntExtra("count", 0)
        Log.d(TAG,"Get pose:$pose,count:$count")

        poseNameTextView.text = pose

        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.CHINESE
            }
        }
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }
    }


    override fun onStart() {
        super.onStart()
        //Log.d(TAG,"onStart启动")
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
        //Log.d(TAG,"OpenCamera ON")
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
                            val poseScore = poseLabels?.find { it.first == pose }?.second ?: 0f
                            poseSmoother.add(poseScore)
                            val avgScore = poseSmoother.average()
                            val roundScore = (avgScore * 100).roundToInt()
                            scoreTextView.text = roundScore.toString()


                            if (posePerformCount < count) {
                                //小于count的处理方法

                                if (!isPoseCompleted) {
                                    //pose已经进入新的评分周期
                                    if (avgScore >= poseThreshold) {
                                        //评分大于阈值的处理
                                        //Log.d(TAG,"AvgScore:$avgScore")
                                        if (isPoseStandardMet) {
                                            //评分大于阈值，且如果已经在大于阈值的处理流程中，则计算时长，满足时长则计数
                                            if (poseStartTime == 0L) {
                                                poseStartTime = System.currentTimeMillis()
                                                isPoseCompleted = false
                                            } else {
                                                if (System.currentTimeMillis() - poseStartTime >= poseDuration) {
                                                    isPoseStandardMet = false
                                                    isPoseCompleted = true
                                                    posePerformCount++
                                                    poseCountTextView.text = posePerformCount.toString()
                                                    Log.d(TAG,"count++:$posePerformCount")
                                                }
                                                else {
                                                    isPoseCompleted = false
                                                }
                                            }

                                        } else {
                                            //如果是首次进入大于阈值的处理流程，则赋值
                                            isPoseStandardMet = true
                                            poseStartTime = System.currentTimeMillis()

                                        }

                                    } else {
                                        //Log.d(TAG,"avgScore:$avgScore")
                                        //评分小于阈值的处理逻辑
                                        if (isPoseStandardMet){
                                            //评分小于阈值，且之前已经满足过标准的
                                            if (poseLowScoreStartTime == 0L) {
                                                //已经达成标准，但首次低于阈值处理
                                                poseLowScoreStartTime = System.currentTimeMillis()

                                            } else {
                                                //已经不是首次低于阈值，需要判断是否超出最长时间
                                                if (System.currentTimeMillis() - poseLowScoreStartTime >= poseExtendedDuration) {
                                                    //如果超出允许错误动作时长，则将isPoseStandardMet置为否，且提醒用户重新开始
                                                    isPoseStandardMet = false
                                                    isPoseCompleted = false
                                                    poseLowScoreStartTime = 0L
                                                    speak(getString(R.string.pose_restart))
                                                } else {
                                                    //如果没有超出允许动作时长，则不处理，可以考虑变色提醒
                                                }
                                            }
                                        } else {
                                            //评分小于阈值，但之前没有满足过标准，不处理
                                            //Log.d(TAG,"less than throne, but never ever meet standard")
                                        }
                                    }
                                } else {
                                    //isPoseCompleted 为true
                                    if (avgScore < poseThreshold) {
                                        //如果小于阈值，则重新开始赋值
                                        isPoseCompleted = false
                                    }
                                }
                            } else {
                                //等于count的处理方法
                                speak(getString(R.string.breakMSG))
                                Log.d(TAG,"Pose finished,poseCount:$posePerformCount,count:$count")
                                returnResultSuccess()
                                finish()
                            }
                        }

                    }).apply {
                        prepareCamera()
                        //Log.d(TAG,"prepareCamera")
                    }
                isPoseClassifier()
                //Log.d(TAG,"isPoseClassifier")
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                    //Log.d(TAG,"camera init")
                }
            }
            createPoseEstimator()
            //Log.d(TAG,"PoseEstimator")
        }
    }

    private fun returnResultSuccess(){
        val returnIntent = Intent()
        returnIntent.putExtra("resultString", "success")
        setResult(Activity.RESULT_OK, returnIntent)

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