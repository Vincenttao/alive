package com.alivehealth.alive.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomNavItem (val title: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    Chat("助手", Icons.Filled.Person),
    Recommendations("推荐", Icons.Filled.Favorite),
    Profile("我的", Icons.Filled.AccountBox)
}