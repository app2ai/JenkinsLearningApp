package com.example.jenkinslearning.feature.credentials

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jenkinslearning.domain.model.ConfigConcept
import com.example.jenkinslearning.domain.model.CredentialGuideEntry
import com.example.jenkinslearning.domain.usecase.GetCredentialGuideUseCase
import com.example.jenkinslearning.presentation.components.AppTopBar
import com.example.jenkinslearning.presentation.components.CodeBlock
import com.example.jenkinslearning.presentation.components.KeyValueRow
import com.example.jenkinslearning.presentation.components.ScreenScaffold
import com.example.jenkinslearning.presentation.components.SectionCard
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import org.koin.compose.viewmodel.koinViewModel

// ----- Contract -----

sealed interface CredentialsIntent {
    data class ToggleCredential(val id: String) : CredentialsIntent
}

data class CredentialsState(
    val credentials: List<CredentialGuideEntry> = emptyList(),
    val concepts: List<ConfigConcept> = emptyList(),
    val expandedId: String? = null,
)

/** This screen has no one-time effects. */
sealed interface CredentialsEffect

// ----- ViewModel -----

/**
 * Shows illustrative credential METADATA only. There is deliberately no code
 * path that reads secrets from Jenkins: the Jenkins API doesn't expose
 * decrypted credentials to normal users, and the app must not try.
 */
class CredentialsViewModel(
    getCredentialGuide: GetCredentialGuideUseCase,
) : MviViewModel<CredentialsIntent, CredentialsState, CredentialsEffect>(CredentialsState()) {

    init {
        val guide = getCredentialGuide()
        setState { copy(credentials = guide.credentials, concepts = guide.concepts) }
    }

    override fun handleIntent(intent: CredentialsIntent) {
        when (intent) {
            is CredentialsIntent.ToggleCredential -> setState {
                copy(expandedId = if (expandedId == intent.id) null else intent.id)
            }
        }
    }
}

// ----- UI -----

@Composable
fun CredentialsRoute(viewModel: CredentialsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ScreenScaffold(topBar = { AppTopBar("Credential Learning Center") }, snackbarHostState = snackbar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "These are the credential IDs this project's Jenkinsfile uses. Only metadata is shown. " +
                        "The app never reads or displays secret values.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Text("Credentials used by the pipeline", style = MaterialTheme.typography.titleSmall) }
            items(state.credentials, key = { it.id }) { entry ->
                CredentialCard(
                    entry = entry,
                    expanded = state.expandedId == entry.id,
                    onToggle = { viewModel.onIntent(CredentialsIntent.ToggleCredential(entry.id)) },
                )
            }
            item { Text("Where should a value live?", style = MaterialTheme.typography.titleSmall) }
            items(state.concepts, key = { it.title }) { concept -> ConceptCard(concept) }
        }
    }
}

@Composable
private fun CredentialCard(entry: CredentialGuideEntry, expanded: Boolean, onToggle: () -> Unit) {
    SectionCard(title = entry.id, modifier = Modifier.clickable(onClick = onToggle)) {
        KeyValueRow("Type", entry.kind.displayName)
        KeyValueRow("Scope", entry.scope.displayName)
        Text(entry.purpose, style = MaterialTheme.typography.bodyMedium)
        if (expanded) {
            Text("Configured at", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(entry.configuredAt, style = MaterialTheme.typography.bodySmall)
            Text("Consumed by", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(entry.consumedBy, style = MaterialTheme.typography.bodySmall)
            CodeBlock(entry.pipelineSnippet)
        } else {
            Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ConceptCard(concept: ConfigConcept) {
    SectionCard(title = concept.title) {
        KeyValueRow("Lives in", concept.whereItLives)
        KeyValueRow("Read by", concept.whoReadsIt)
        KeyValueRow("Secrets?", concept.safeForSecrets)
        CodeBlock(concept.example)
    }
}
