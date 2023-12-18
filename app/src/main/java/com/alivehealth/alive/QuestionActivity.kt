package com.alivehealth.alive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

private const val TAG="测试->QuestionActivity"

class QuestionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel: QuestionViewModel by viewModels()
        val questionsData = loadQuestionsFromJson()
        viewModel.setQuestions(questionsData.questions) // 假设 viewModel 只处理问题列表

        val logic = questionsData.logic.answerSequence


        setContent {
            MaterialTheme {
                QuestionScreen(viewModel, logic)
            }
        }
    }



    private fun loadQuestionsFromJson(): QuestionsData {
        val gson = Gson()
        val jsonFile = resources.openRawResource(R.raw.questions) // JSON 文件位于 res/raw/questions.json
        val reader = InputStreamReader(jsonFile)
        val questionsDataType = object : TypeToken<QuestionsData>() {}.type
        val questionsData: QuestionsData = gson.fromJson(reader, questionsDataType)
        reader.close()
        Log.d(TAG, "Json data loaded: ${questionsData.questions.size} questions")
        Log.d(TAG,"Json object:$questionsData")
        Log.d(TAG, "Loaded logic data: ${questionsData.logic.answerSequence}")
        return questionsData
    }


    @Composable
    fun QuestionScreen(viewModel: QuestionViewModel = viewModel(), logic: Map<String, String>) {
        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IntroductionCard(title = "健康问答", description = "回答下列问题以获取个性化的健康建议。")

                    val question = viewModel.currentQuestion
                    question?.let{
                        QuestionCard(question = (it), onOptionSelected ={ nextId,answer ->
                            viewModel.onOptionSelected(nextId, answer)
                        } )
                    } ?: run {
                        val recommendation = viewModel.getRecommendation((logic))
                        Text("问答结束。推荐课程：${recommendation ?:"无"}")
                    }
                }
            }
        }

    }

    @Composable
    fun IntroductionCard(title: String, description: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), // 添加间距分隔介绍卡片和问答卡片
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    fun QuestionCard(question: QuestionItem, onOptionSelected: (Int?, String) -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(

                    text = question.text,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    question.options.forEach { option ->
                        Button(
                            onClick = { onOptionSelected(option.nextQuestion, option.text) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(option.text)
                        }
                    }
                }
            }
        }
    }


}

data class QuestionsData(
    val questions: List<QuestionItem>,
    val recommendations: Map<String, Recommendation>,
    val logic: Logic
)

data class Recommendation(
    val courseId: String,
    val description: String
)

data class Logic(
    val answerSequence: Map<String, String>
)

data class QuestionItem(
    val id: Int,
    val text: String,
    val options: List<Option>
)

data class Option(
    val text: String,
    val nextQuestion: Int?
)



class QuestionViewModel : ViewModel() {
    var currentQuestion by mutableStateOf<QuestionItem?>(null)
        private set

    private var questionList: List<QuestionItem> = listOf()
    private var currentQuestionIndex = 0

    private var userAnswers = mutableListOf<String?>()

    fun setQuestions(questions: List<QuestionItem>) {
        questionList = questions
        currentQuestionIndex = 0
        currentQuestion = questionList.firstOrNull()

        //根据问题数量初始化 userAnswers
        userAnswers = MutableList(questions.size) {"None"}
        Log.d(TAG, "Questions set: ${questionList.size} questions")
    }

    fun onOptionSelected(nextQuestionId: Int?, answer: String) {
        val formattedAnswer = when(answer) {
            "是" -> "1"
            "否" -> "0"
            else -> "None"
        }
        if (currentQuestionIndex in userAnswers.indices) {
            userAnswers[currentQuestionIndex] = formattedAnswer
            Log.d(TAG, "Answer recorded for question $currentQuestionIndex: $answer -> $formattedAnswer")
        }

        nextQuestionId?.let { nextId ->
            currentQuestionIndex = questionList.indexOfFirst { question -> question.id == nextId }
            currentQuestion = questionList.getOrNull(currentQuestionIndex)
            Log.d(TAG, "Next question index: $currentQuestionIndex")
        } ?: run {
            // 当没有下一个问题时，这里可以处理结束逻辑
            currentQuestion = null
            Log.d(TAG, "No more questions. Quiz ended.")
        }
    }

    fun getRecommendation(logic: Map<String,String>): String? {
        val answerSequence = userAnswers.joinToString(separator = "")
        Log.d(TAG,"Answer sequence is $answerSequence")

        val recommendationResult = logic[answerSequence]
        Log.d(TAG,"Recommendation is $recommendationResult")
        return logic[answerSequence]
    }
}