package com.alivehealth.alive.ml

import android.content.Context
import com.alivehealth.alive.data.Person
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil


/**
 * 这段代码定义了一个 PoseClassifier 类，它负责运行姿态分类的机器学习模型，
 * 通过接受识别后的用户关键姿态，模型计算后输出识别出的姿态名字和置信度
 * * 构造函数：接收一个 Interpreter 对象（这是 TensorFlow Lite 的模型解释器）和一个标签列表。
 * 这些标签对应于模型可以识别的姿态类别。
 * 伴生对象（Companion object）：

 * 分类方法（classify）：
 *
 * 接收一个 Person 对象（可能为 null），它包含了从姿态估计模型中获得的关键点数据。
 * 预处理 Person 对象中的关键点数据，将其转换为一个浮点数数组，这是模型的输入格式。
 * 使用解释器运行模型，并将预处理后的输入数据传递给模型。
 * 处理模型的输出，将其与标签列表中的类别名配对，并将它们存储在一个列表中返回。
 * 关闭方法（close）：关闭解释器以释放资源。
 *
 * 这个类的作用是将从姿态估计模型中获得的关键点数据用于分类，确定一个人的姿态属于哪个预定义的类别，
 * 并给出相应的置信度分数。通过这种方式，可以对人的姿态进行分类，例如判断一个人是站立、跑步还是进行其他活动。
 */
class PoseClassifier(
    private val interpreter: Interpreter,
    private val labels: List<String>
) {
    private val input = interpreter.getInputTensor(0).shape()//根据Tensor的要求设定输入的格式
    private val output = interpreter.getOutputTensor(0).shape()//确定输出的格式

    companion object {
        //private const val MODEL_FILENAME = "classifier.tflite"//模型目标文件
        //private const val LABELS_FILENAME = "labels.txt"//分类标签
        private const val CPU_NUM_THREADS = 4

        /**
         * 修改后的 create 方法，接收模型文件名和标签文件名作为参数。
         */
        fun create(context: Context, modelFilename: String, labelsFilename: String): PoseClassifier {
            val options = Interpreter.Options().apply {
                setNumThreads(CPU_NUM_THREADS)
            }
            return PoseClassifier(
                Interpreter(
                    FileUtil.loadMappedFile(
                        context, modelFilename
                    ), options
                ),
                FileUtil.loadLabels(context, labelsFilename)
            )
        }
    }


    /**
     * 输入的是识别后的用户姿态信息，然后再做二次处理输出如下
     * [
     *     Pair("chair", 0.05),
     *     Pair("cobra", 0.03),
     *     Pair("dog", 0.02),
     *     Pair("tree", 0.88),
     *     Pair("warrior", 0.02)
     * ]
     */
    fun classify(person: Person?): List<Pair<String, Float>> {
        // Preprocess the pose estimation result to a flat array
        val inputVector = FloatArray(input[1]) //创建一个FloatArray，名为inputVector，其大小由输入张量（input）的第二维决定

        //Person对象的keyPoints进行遍历（如果keyPoints非空）。在遍历过程中，将每个关键点的y坐标、x坐标和分数分别存储到inputVector的相应位置。
        person?.keyPoints?.forEachIndexed { index, keyPoint ->
            inputVector[index * 3] = keyPoint.coordinate.y
            inputVector[index * 3 + 1] = keyPoint.coordinate.x
            inputVector[index * 3 + 2] = keyPoint.score
        }

        // Postprocess the model output to human readable class names
        val outputTensor = FloatArray(output[1])
        interpreter.run(arrayOf(inputVector), arrayOf(outputTensor))
        val output = mutableListOf<Pair<String, Float>>()
        outputTensor.forEachIndexed { index, score ->
            output.add(Pair(labels[index], score))
        }
        return output
    }

    fun close() {
        interpreter.close()
    }

}