package com.alivehealth.alive.data

data class ExerciseInfo(
    val id:Int,
    val name: String,
    val sequence: Int,
    val completed: Int
)

data class DailyExercise(
    val course_info: CourseInfo,
    val exercises_info: List<ExerciseInfo>
)

data class ExerciseDataResult(
    val courseInfo: CourseInfo,
    val filteredExercises : List<ExerciseInfo>
)

data class ExerciseHistory(
    val exercise_id: Int,
    val date_completed: String,
    val score: Int,
    val durationtime: Int,
    val completed: Int // Assuming 0 for not completed, 1 for completed
)

data class CourseInfo(
    val course_id: Int,
    val course_name: String,
    val start_date: String,
    val frequency: String
)

data class DailyStatistics(
    val date: String,
    val averageScore: Double,
    val totalCount: Int,
    //val totalDuration: Int
)

data class GridItem(
    val title: String,
    val icon: Int,
    val onClick: () -> Unit)
