package com.alivehealth.alive

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 3000) // 3秒后跳转到主Activity
    }
}

@Composable
fun SplashScreen() {
    val orangeColor = getMyColor()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(orangeColor), // 或您的背景颜色
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_background),
            contentDescription = "Background Image"
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally

        ) {
            Text(
                text = "Alive Health",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 36.sp // 标题大小
                )
            )
            Text(
                text = "让老年生活更加美好",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp // 较小字体
                )
            )
        }
    }
}

@Composable
fun getMyColor(): Color {
    val context = LocalContext.current
    return Color(ContextCompat.getColor(context, R.color.orange))
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashScreen()
}