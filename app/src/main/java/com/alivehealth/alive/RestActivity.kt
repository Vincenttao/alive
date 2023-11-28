package com.alivehealth.alive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RestActivity : AppCompatActivity(){

    private val TAG = "Rest+测试"
    private var duration : Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        duration = (intent.getStringExtra("duration") ?: "").toLong()
        Log.d(TAG,"rest start:$duration")

        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            Log.d(TAG,"Finished rest, duration:$duration")
            returnResultSuccess()
            finish()
        }
    }

    private fun returnResultSuccess(){
        val returnIntent = Intent()
        returnIntent.putExtra("resultString", "success")
        setResult(Activity.RESULT_OK, returnIntent)
    }


}


