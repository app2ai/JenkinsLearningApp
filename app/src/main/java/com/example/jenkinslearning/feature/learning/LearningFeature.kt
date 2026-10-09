package com.example.jenkinslearning.feature.learning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jenkinslearning.domain.model.LearningTopic
import com.example.jenkinslearning.domain.usecase.GetLearningTopicsUseCase
import com.example.jenkinslearning.presentation.components.AppTopBar
import com.example.jenkinslearning.presentation.components.CodeBlock
import com.example.jenkinslearning.presentation.components.ScreenScaffold
import com.example.jenkinslearning.presentation.components.SectionCard
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import org.koin.compose.viewmodel.koinViewModel

sealed interface LearningIntent {
    data class ToggleTopic(val id: String) : LearningIntent
}

data class LearningState(
    val topics: List<LearningTopic> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
)

sealed interface LearningEffect

class LearningViewModel(
    getTopics: GetLearningTopicsUseCase,
) : MviViewModel<LearningIntent, LearningState, LearningEffect>(LearningState()) {

    init {
        setState { copy(topics = getTopics()) }
    }

    override fun handleIntent(intent: LearningIntent) {
        when (intent) {
            is LearningIntent.ToggleTopic -> setState {
                copy(expandedIds = if (intent.id in expandedIds) expandedIds - intent.id else expandedIds + intent.id)
            }
        }
    }
}

@Composable
fun LearningRoute(viewModel: LearningViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ScreenScaffold(topBar = { AppTopBar("Learning Center") }, snackbarHostState = snackbar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.topics, key = { it.id }) { topic ->
                val expanded = topic.id in state.expandedIds
                SectionCard(
                    title = topic.title,
                    modifier = Modifier.clickable { viewModel.onIntent(LearningIntent.ToggleTopic(topic.id)) },
                ) {
                    Text(topic.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expanded) {
                        Text(topic.body, style = MaterialTheme.typography.bodyMedium)
                        topic.codeSample?.let { CodeBlock(it) }
                    }
                }
            }
        }
    }
}
