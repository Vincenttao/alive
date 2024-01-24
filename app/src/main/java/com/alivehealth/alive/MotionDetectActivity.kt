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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.alivehealth.alive.camera.CameraSource
import com.alivehealth.alive.data.Device
import com.alivehealth.alive.data.PoseDetectionLogic
import com.alivehealth.alive.ml.ModelType
import com.alivehealth.alive.ml.MoveNet
import com.alivehealth.alive.ml.MoveNetMultiPose
import com.alivehealth.alive.ml.PoseClassifier
import com.alivehealth.alive.ml.PoseNet
import com.alivehealth.alive.ml.Type
import com.alivehealth.alive.motiondetectlogic.PoseDetectionManager
import com.google.android.material.button.MaterialButton
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG="MotionDetect+测试"



//


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

    private lateinit var skipExerciseButton: MaterialButton


    private lateinit var pose: String
    private lateinit var poseClassifier: String
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

        skipExerciseButton = findViewById((R.id.skipExerciseButton))

        setupSkipExerciseButton()


        pose = intent.getStringExtra("name") ?:""
        val count = intent.getIntExtra("count", 1)
        val exerciseId = intent.getIntExtra("exerciseId",0)
        val poseDuration = (intent.getIntExtra("poseDuration",1).toLong() )* 1000
        val logicString = intent.getStringExtra("logic") ?: "COUNT_ABOVE_THRESHOLD" // 默认值
        val detectionLogic = enumValueOf<PoseDetectionLogic>(logicString)
        poseClassifier = intent.getStringExtra("poseClassifier") ?: "pose_01"

        Log.d(TAG,"onCreate: Received pose: $pose, count: $count; poseDuration:$poseDuration; logicString:${logicString}; poseClassifier:$poseClassifier")
        Log.d(TAG,"detectLogic:$detectionLogic")

        //poseNameTextView.text = pose

        val activityStartTime = System.currentTimeMillis()

        poseDetectionManager = PoseDetectionManager(
            context = this,
            exerciseId = exerciseId,
            activityStartTime = activityStartTime,
            targetCount = count,
            poseDurationThreshold = poseDuration,
            detectionLogic = detectionLogic
            //poseThreshold = poseThreshold,
            //poseDuration = poseDuration,
            //poseExtendedDuration = poseExtendedDuration
        ) { progressBarProgress:Int, currentPose:String, textInMiddle:String, progressBarMax:Int ->
            // 在这里更新 UI，例如显示分数和当前姿势
            runOnUiThread{
                poseNameTextView.text = currentPose
                textInMiddleTextView.text = textInMiddle
                poseScoreProgressBar.progressMax = progressBarMax.toFloat()
                poseScoreProgressBar.progress = progressBarProgress.toFloat()
            }

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


    private fun setupSkipExerciseButton(){
        skipExerciseButton.setOnClickListener {
            poseDetectionManager.finishPoseDetection()
        }
    }


    private fun isPoseClassifier() {
        val modelFilename = "$poseClassifier.tflite"
        val labelsFilename = "${poseClassifier}_labels.txt"
        cameraSource?.setClassifier(if (isClassifyPose) PoseClassifier.create(this,modelFilename,labelsFilename) else null)
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
