package com.alivehealth.alive
//通过接受courseId来判断要执行多少次，已经弃用

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.xmlpull.v1.XmlPullParser

class CourseActivity : AppCompatActivity() {
    // ...

    private val TAG = "Course+测试"
    private var currentElementIndex: Int = 0//course index计数
    private lateinit var courseCode: String//接收从MainActivity传递的参数

    data class Pose(val name: String, val count: Int)
    data class Rest(val duration: Long)
    data class Course(val id: String, val elements: List<Any>)//这个类用于存储一个完整课程的所有信息，包括课程的唯一标识和构成课程的各个元素（即姿势和休息时间）。

    private var course: Course? = null

    //接收从MainActivity回传信息，并判断是否增加index
    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultString = result.data?.getStringExtra("resultString") ?: ""
            if (resultString=="success") {
                currentElementIndex ++
                if(currentElementIndex < course?.elements?.size!!) {
                    Log.d(TAG,"Receive result:$resultString, continued course,index:$currentElementIndex")
                    startCourse(course)
                }
            }

        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        courseCode = intent.getStringExtra("COURSE_CODE") ?: ""//接受从MainActivity传递过来的课程参数
        Log.d(TAG,"Recived from Main, coursecode:$courseCode")
        course = parseCourse(courseCode)//解析xml，获取课程详细情况
        Log.d(TAG,"course:$course")

    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG,"onStart,start course")
        startCourse(course)

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

    private fun startCourse(course: Course?){
        Log.d(TAG,"Start Course Func, index:$currentElementIndex")
        course?.elements?.let { elements ->
            if (currentElementIndex < elements.size) {
                when (val element = elements[currentElementIndex]) {
                    is Pose ->
                    {
                        //唤起motionDetected 并传递参数
                        val intent = Intent(this, MotionDetectActivity::class.java)
                        intent.putExtra("name", element.name)
                        intent.putExtra("count", element.count)
                        resultLauncher.launch(intent)
                        Log.d(TAG,"is Pose, Start motionDetect with name:${element.name},count:${element.count}")

                    }
                    is Rest ->
                    {
                        //唤起restActivity 并传递参数
                        val intent = Intent(this, RestActivity::class.java)
                        intent.putExtra("duration",element.duration)
                        resultLauncher.launch(intent)
                        Log.d(TAG,"is Rest, Start Rest with duration:$intent")
                    }

                    else -> {}
                }
            } else {
                //结束并回到首页
                finish()
                Log.d(TAG,"finished, index:$currentElementIndex")
            }
        }
    }

}
