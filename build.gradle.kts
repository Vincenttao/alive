// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.3" apply false
    kotlin("android") version "1.9.21" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral() // jcenter()已经宣布过时，推荐使用mavenCentral()
    }
    dependencies {
        val kotlin_version = "1.9.21" // 使用新版本的Kotlin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}