package com.alivehealth.alive

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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
import com.alivehealth.alive.ml.TrackerType
import com.alivehealth.alive.ml.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Locale
import kotlin.math.roundToInt

class MotionDetectActivity : AppCompatActivity() {

    // companion object 用于声明静态成员，类似于 Java 中的 static 字段。在这个例子中，它定义了一个字符串常量。
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
        private const val TREE_POSE_THRESHOLD = 0.5f
        private const val TREE_POSE_DURATION = 3000L // 3秒
        private const val TREE_POSE_EXTENDED_DURATION = 5000L
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
    private lateinit var tvScore: TextView
    private lateinit var tvFPS: TextView
    private lateinit var spnDevice: Spinner
    private lateinit var spnModel: Spinner
    private lateinit var spnTracker: Spinner
    private lateinit var vTrackerOption: View
    private lateinit var tvClassificationValue1: TextView
    private lateinit var tvClassificationValue2: TextView
    private lateinit var tvClassificationValue3: TextView
    private lateinit var swClassification: SwitchCompat
    private lateinit var vClassificationOption: View

    private lateinit var textToSpeech: TextToSpeech

    private lateinit var scoreTextView: TextView
    private lateinit var courseCode: String




    // cameraSource 可能为空，表示它可能没有被初始化。
    private var cameraSource: CameraSource? = null
    private var isClassifyPose = true

    //下面的变量用于提示用户监测结果
    private val treePoseSmoother = MovingAverage(10)
    private var treePoseStartTime: Long = 0
    private var isTreePoseStandardMet = false
    private var isTreePoseCompleted = false



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

    /*
    private var changeModelListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
            // ... 实现了 onNothingSelected 和 onItemSelected 方法，用于处理模型更改。
        }

        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            changeModel(position)
        }
    }

    private var changeDeviceListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            changeDevice(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // do nothing
        }
    }

     */

    /*
    private var changeTrackerListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            changeTracker(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // do nothing
        }
    }

     */

    /*
    private var setClassificationListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            showClassificationResult(isChecked)
            isClassifyPose = isChecked
            isPoseClassifier()
        }

     */




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motiondetect)
        // keep screen on while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /*
        tvScore = findViewById(R.id.tvScore)//将一个object对象赋值给tvScore，这样将来修改tvScore就可以修改对象的值
        tvFPS = findViewById(R.id.tvFps)
        spnModel = findViewById(R.id.spnModel)
        spnDevice = findViewById(R.id.spnDevice)
        spnTracker = findViewById(R.id.spnTracker)
        vTrackerOption = findViewById(R.id.vTrackerOption)

        tvClassificationValue1 = findViewById(R.id.tvClassificationValue1)
        tvClassificationValue2 = findViewById(R.id.tvClassificationValue2)
        tvClassificationValue3 = findViewById(R.id.tvClassificationValue3)
        swClassification = findViewById(R.id.swPoseClassification)
        vClassificationOption = findViewById(R.id.vClassificationOption)
        initSpinner()
        spnModel.setSelection(modelPos)
        swClassification.setOnCheckedChangeListener(setClassificationListener)

         */

        surfaceView = findViewById(R.id.surfaceView)

        scoreTextView = findViewById(R.id.scoreTextView)

        courseCode = intent.getStringExtra("COURSE_CODE") ?: ""//接受从MainActivity传递过来的课程参数

        //姿势检测需要的赋值



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
                            /*
                            tvScore.text = getString(R.string.tfe_pe_tv_score, personScore ?: 0f)
                            poseLabels?.sortedByDescending { it.second }?.let {
                                tvClassificationValue1.text = getString(
                                    R.string.tfe_pe_tv_classification_value,
                                    convertPoseLabels(if (it.isNotEmpty()) it[0] else null)
                                )
                                tvClassificationValue2.text = getString(
                                    R.string.tfe_pe_tv_classification_value,
                                    convertPoseLabels(if (it.size >= 2) it[1] else null)
                                )
                                tvClassificationValue3.text = getString(
                                    R.string.tfe_pe_tv_classification_value,
                                    convertPoseLabels(if (it.size >= 3) it[2] else null)
                                )
                            }

                             */

                            //计算姿势的得分
                            val poseScore = poseLabels?.find { it.first == courseCode }?.second ?: 0f
                            treePoseSmoother.add(poseScore)
                            val avgTreeScore = treePoseSmoother.average()
                            val roundTreeScore = (avgTreeScore*100).roundToInt()
                            scoreTextView.text = roundTreeScore.toString()

                            val poseDuration = resources.getInteger(R.integer.pose_duration).toLong()
                            val poseExtendedDuration = resources.getInteger(R.integer.pose_extended_duration).toLong()
                            val poseThreshold = resources.getString(R.string.pose_threshold).toFloat()

                            if (avgTreeScore >= poseThreshold && !isTreePoseStandardMet) {
                                treePoseStartTime = System.currentTimeMillis()
                                isTreePoseStandardMet = true
                                speak(getString(R.string.tree_pose_standard_met))
                            } else if (isTreePoseStandardMet) {
                                val elapsedTime = System.currentTimeMillis() - treePoseStartTime
                                if (elapsedTime >= poseDuration && !isTreePoseCompleted) {
                                    isTreePoseCompleted = true
                                    speak(getString(R.string.tree_pose_completed))
                                } else if (elapsedTime < poseExtendedDuration && avgTreeScore < poseThreshold) {
                                    isTreePoseStandardMet = false
                                    speak(getString(R.string.tree_pose_restart))
                                }
                            }
                        }

                    }).apply {
                        prepareCamera()
                    }
                isPoseClassifier()
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                }
            }
            createPoseEstimator()
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

    /*
    // Initialize spinners to let user select model/accelerator/tracker.
    private fun initSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_models_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spnModel.adapter = adapter
            spnModel.onItemSelectedListener = changeModelListener
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_device_name, android.R.layout.simple_spinner_item
        ).also { adaper ->
            adaper.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spnDevice.adapter = adaper
            spnDevice.onItemSelectedListener = changeDeviceListener
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_tracker_array, android.R.layout.simple_spinner_item
        ).also { adaper ->
            adaper.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spnTracker.adapter = adaper
            spnTracker.onItemSelectedListener = changeTrackerListener
        }
    }

    // Change model when app is running
    private fun changeModel(position: Int) {
        if (modelPos == position) return
        modelPos = position
        createPoseEstimator()
    }

    // Change device (accelerator) type when app is running
    private fun changeDevice(position: Int) {
        val targetDevice = when (position) {
            0 -> Device.CPU
            1 -> Device.GPU
            else -> Device.NNAPI
        }
        if (device == targetDevice) return
        device = targetDevice
        createPoseEstimator()
    }

    // Change tracker for Movenet MultiPose model
    private fun changeTracker(position: Int) {
        cameraSource?.setTracker(
            when (position) {
                1 -> TrackerType.BOUNDING_BOX
                2 -> TrackerType.KEYPOINTS
                else -> TrackerType.OFF
            }
        )
    }

     */

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

    /*
    // Show/hide the pose classification option.
    private fun showPoseClassifier(isVisible: Boolean) {
        vClassificationOption.visibility = if (isVisible) View.VISIBLE else View.GONE
        if (!isVisible) {
            swClassification.isChecked = false
        }
    }

    // Show/hide the detection score.
    private fun showDetectionScore(isVisible: Boolean) {
        tvScore.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    // Show/hide classification result.
    private fun showClassificationResult(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        tvClassificationValue1.visibility = visibility
        tvClassificationValue2.visibility = visibility
        tvClassificationValue3.visibility = visibility
    }

    // Show/hide the tracking options.
    private fun showTracker(isVisible: Boolean) {
        if (isVisible) {
            // Show tracker options and enable Bounding Box tracker.
            vTrackerOption.visibility = View.VISIBLE
            spnTracker.setSelection(1)
        } else {
            // Set tracker type to off and hide tracker option.
            vTrackerOption.visibility = View.GONE
            spnTracker.setSelection(0)
        }
    }

     */

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