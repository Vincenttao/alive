package com.alivehealth.alive

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
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
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG="MotionDetect+测试"
private var frameCount = 0

enum class PoseDetectionLogic {
    COUNT_ABOVE_THRESHOLD,
    TIME_ABOVE_THRESHOLD,
    PURE_TIME
}

class MotionDetectActivity : AppCompatActivity() {
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

    // 以下各个 late init 变量是 UI 组件的声明，它们也会在之后被初始化。保留后续处理

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var poseNameTextView: TextView
    private lateinit var textInMiddleTextView: TextView
    //private lateinit var poseScoreProgressBar : ProgressBar
    private lateinit var poseScoreProgressBar : CircularProgressBar//三方进度圈插件
    //private lateinit var videoView: VideoView


    private lateinit var pose: String
    // cameraSource 可能为空，表示它可能没有被初始化。
    private var cameraSource: CameraSource? = null
    private val isClassifyPose = true

    private lateinit var poseDetectionManager: PoseDetectionManager

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
        supportActionBar?.hide() // 隐藏标题栏
        Log.d(TAG,"onCreate: Activity Created.")
        setContentView(R.layout.activity_motiondetect)
        // keep screen on while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.surfaceView)
        poseNameTextView = findViewById(R.id.poseNameTextView)
        poseScoreProgressBar = findViewById(R.id.poseScorePB)
        textInMiddleTextView = findViewById(R.id.textInMidlle)

        /*
        videoView = findViewById(R.id.videoView)
        val videoPath = "android.resource://${packageName}/${R.raw.video_1}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true // 循环播放
            videoView.start() // 开始播放
        }
        */

        pose = intent.getStringExtra("name") ?:""
        val count = intent.getIntExtra("count", 1)
        val poseDuration = intent.getIntExtra("poseDuration",1000).toLong()
        val logicString = intent.getStringExtra("logic") ?: "COUNT_ABOVE_THRESHOLD" // 默认值
        val detectionLogic = enumValueOf<PoseDetectionLogic>(logicString)
        val poseClassifier = intent.getStringExtra("poseClassifier") ?: "pose_01"

        Log.d(TAG,"onCreate: Received pose: $pose, count: $count; poseDuration:$poseDuration; logicString:${logicString}; poseClassifier:$poseClassifier")
        Log.d(TAG,"detectLogic:$detectionLogic")

        //poseNameTextView.text = pose

        val activityStartTime = System.currentTimeMillis()

        poseDetectionManager = PoseDetectionManager(
            context = this,
            activityStartTime = activityStartTime,
            targetCount = count,
            poseDuration = poseDuration,
            detectionLogic = detectionLogic
            //poseThreshold = poseThreshold,
            //poseDuration = poseDuration,
            //poseExtendedDuration = poseExtendedDuration
        ) { progressBarProgress:Int, currentPose:String, textInMiddle:String, progressBarMax:Int ->
            // 在这里更新 UI，例如显示分数和当前姿势
            poseNameTextView.text = currentPose
            textInMiddleTextView.text = textInMiddle
            poseScoreProgressBar.progressMax = progressBarMax.toFloat()
            poseScoreProgressBar.progress = progressBarProgress.toFloat()
        }

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
        Log.d(TAG,"onStart启动")
        openCamera()
    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()

        /*
        if (!videoView.isPlaying) {
            videoView.start() // 页面可见时重新开始播放
        }

         */


        Log.d(TAG,"onResume started")
    }

    override fun onPause() {
        Log.d(TAG,"onPause started")
        cameraSource?.close()
        cameraSource = null
        super.onPause()
        //videoView.pause() // 页面不可见时暂停播放
    }

    override fun onDestroy() {
        textToSpeech.shutdown()
        Log.d(TAG,"onPause started")
        super.onDestroy()
    }

    // check if permission is granted or not.
    private fun isCameraPermissionGranted(): Boolean {
        val isGranted = checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "isCameraPermissionGranted: Permission is " + if (isGranted) "granted" else "denied")
        return isGranted
    }


    private fun openCamera(){
        Log.d(TAG, "openCamera: Attempting to open camera.")
        if(isCameraPermissionGranted()) {
            if(cameraSource == null) {
                Log.d(TAG,"cameraSource == null")
                cameraSource = CameraSource(surfaceView, object : CameraSource.CameraSourceListener {
                    override fun onFPSListener(fps: Int) {
                        //待实现
                    }
                    override fun onDetectedInfo(
                        personScore: Float?,
                        poseLabels: List<Pair<String, Float>>?
                    ) {
                        poseDetectionManager.updatePoseDetection(poseLabels ?:listOf(), pose)

                    }

                }).apply {
                    prepareCamera()
                    Log.d(TAG,"cameraSource init PrepareCamera")
                }

                isPoseClassifier()
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                    Log.d(TAG,"camera init")
                }

            }
            createPoseEstimator()
            Log.d(TAG,"PoseEstimator")
        }
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
        poseDetector?.let {
            cameraSource?.setDetector(it)
            Log.d(TAG,"Pose detector set: $modelPos")
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
    private val poseThreshold: Float = 0.8f,
    private val poseDuration: Long,
    private val poseExtendedDuration: Long = 1000L,
    private val detectionLogic: PoseDetectionLogic,
    private val updateUI: (progressBarProgress:Int, currentPose:String, textInMiddle:String, progressBarMax:Int) -> Unit // UI更新函数
) {
    private var posePerformCount = 0
    private var poseStartTime: Long = 0L
    private var poseLowScoreStartTime: Long = 0L
    private var isPoseStandardMet = false
    private var isPoseCompleted = false
    private val poseSmoother = MovingAverage(10)
    private var totalScore = 0f
    private var scoreCount = 0

    fun updatePoseDetection(poseLabels: List<Pair<String, Float>>, currentPose: String) {
        frameCount++
        if (frameCount % 100 == 0){
            Log.d(TAG, "updatePoseDetection: Updating pose detection.")
            Log.d(TAG,"Pose detection result:$poseLabels")
        }


        val poseScore = poseLabels.find { it.first == currentPose }?.second ?: 0f
        poseSmoother.add(poseScore)
        val avgScore = poseSmoother.average()
        val roundScore = (avgScore * 100).roundToInt()

        when (detectionLogic) {
            PoseDetectionLogic.COUNT_ABOVE_THRESHOLD -> {
                (context as? Activity)?.runOnUiThread{
                    updateUI(roundScore, currentPose, posePerformCount.toString(), 100)
                    }
                if (posePerformCount < targetCount){
                    handlePoseScoring((avgScore))
                } else{
                    Log.d(TAG,"Target count reached: $posePerformCount")
                    finishPoseDetection()
                }
            }
            PoseDetectionLogic.TIME_ABOVE_THRESHOLD -> {
                val durationTime: Int = if (isPoseStandardMet) {
                    ((System.currentTimeMillis() - poseStartTime )/ 1000).toInt()
                } else {
                    0
                }
                (context as? Activity)?.runOnUiThread{
                    updateUI(roundScore, currentPose, durationTime.toString(), 100)
                }
                if (posePerformCount < targetCount){ //默认为1
                    handlePoseScoring(avgScore)
                } else {
                    Log.d(TAG,"Time reached:$durationTime")
                    finishPoseDetection()
                }
            }
            PoseDetectionLogic.PURE_TIME -> {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - activityStartTime

                // 更新 UI，例如显示已过去的时间
                (context as? Activity)?.runOnUiThread {
                    updateUI(0, currentPose, elapsedTime.toString(),100)
                }

                // 检查是否达到目标时间
                if (elapsedTime >= poseDuration * targetCount) {
                    finishPoseDetection()
                }

            }
        }

    }



    private fun handlePoseScoring(avgScore: Float) {
        if(frameCount % 100 ==0){
            Log.d(TAG, "handlePoseScoring: Handling pose scoring with avgScore: $avgScore")
        }
        if (!isPoseCompleted) {
            if (avgScore >= poseThreshold) {
                totalScore += avgScore
                scoreCount++
                handlePoseAboveThreshold()
            } else {
                handlePoseBelowThreshold()
            }
        } else if (avgScore < poseThreshold) {
            isPoseCompleted = false
        }
    }

    private fun handlePoseAboveThreshold() {
        if (isPoseStandardMet) {
            if (System.currentTimeMillis() - poseStartTime >= poseDuration) {
                posePerformCount++
                isPoseStandardMet = false
                isPoseCompleted = true
            }
        } else {
            isPoseStandardMet = true
            poseStartTime = System.currentTimeMillis()
        }
    }

    private fun handlePoseBelowThreshold() {
        if (isPoseStandardMet) {
            if (poseLowScoreStartTime == 0L) {
                poseLowScoreStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - poseLowScoreStartTime >= poseExtendedDuration) {
                isPoseStandardMet = false
                isPoseCompleted = false
                poseLowScoreStartTime = 0L
            }
        }
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