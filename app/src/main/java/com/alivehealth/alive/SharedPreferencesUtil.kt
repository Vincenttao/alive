package com.alivehealth.alive

import android.content.Context
import com.alivehealth.alive.data.ExerciseInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SharedPreferencesUtil {
    private const val PREFERENCES_FILE_KEY = "com.example.android.sharedpreferences"
    private const val COURSE_NAME_KEY = "courseName"
    private const val EXERCISE_PLAN_KEY = "exercisePlan"

    fun saveCourseName(context: Context, courseName: String) {
        val sharedPref = context.getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(COURSE_NAME_KEY, courseName)
            apply()
        }
    }

    fun loadCourseName(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE)
        return sharedPref.getString(COURSE_NAME_KEY, "") ?: ""
    }

    fun saveExercisePlan(context: Context, exercisePlan: List<ExerciseInfo>) {
        val gson = Gson()
        val json = gson.toJson(exercisePlan)
        val sharedPref = context.getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(EXERCISE_PLAN_KEY, json)
            apply()
        }
    }

    fun loadExercisePlan(context: Context): List<ExerciseInfo> {
        val sharedPref = context.getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE)
        val json = sharedPref.getString(EXERCISE_PLAN_KEY, null)
        return if (json != null) {
            val gson = Gson()
            val type = object : TypeToken<List<ExerciseInfo>>() {}.type
            gson.fromJson(json, type)
        } else {
            listOf()
        }
    }
}
