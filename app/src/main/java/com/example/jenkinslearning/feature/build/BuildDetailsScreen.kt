package com.example.jenkinslearning.feature.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jenkinslearning.core.common.TimeFormat
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.presentation.components.AppTopBar
import com.example.jenkinslearning.presentation.components.CodeBlock
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.components.ErrorState
import com.example.jenkinslearning.presentation.components.KeyValueRow
import com.example.jenkinslearning.presentation.components.LoadingState
import com.example.jenkinslearning.presentation.components.RefreshAction
import com.example.jenkinslearning.presentation.components.SampleDataBanner
import com.example.jenkinslearning.presentation.components.ScreenScaffold
import com.example.jenkinslearning.presentation.components.SectionCard
import com.example.jenkinslearning.presentation.components.SmallSpinner
import com.example.jenkinslearning.presentation.components.StatusChip
import com.example.jenkinslearning.presentation.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BuildDetailsRoute(
    jobFullName: String,
    buildNumber: Int,
    onBack: () -> Unit,
    viewModel: BuildDetailsViewModel = koinViewModel(key = "build:$jobFullName#$buildNumber") {
        parametersOf(jobFullName, buildNumber)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    // Tell the ViewModel when the screen is visible so it only polls then.
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(BuildDetailsIntent.ScreenStarted)
        onStopOrDispose { viewModel.onIntent(BuildDetailsIntent.ScreenStopped) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BuildDetailsEffect.OpenUrl ->
                runCatching { uriHandler.openUri(effect.url) }
                    .onFailure { snackbar.showSnackbar("No app can open this link.") }
            is BuildDetailsEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    ScreenScaffold(
        topBar = {
            AppTopBar("${state.jobFullName} #${state.buildNumber}", onBack = onBack, actions = {
                if (state.isPolling) SmallSpinner()
                RefreshAction({ viewModel.onIntent(BuildDetailsIntent.Refresh) }, enabled = !state.isLoading)
            })
        },
        snackbarHostState = snackbar,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val details = state.details
        val error = state.error
        when {
            state.isLoading -> LoadingState(modifier = modifier)
            error != null -> ErrorState(error, onRetry = { viewModel.onIntent(BuildDetailsIntent.Refresh) }, modifier = modifier)
            details != null -> BuildDetailsContent(state, details, viewModel::onIntent, modifier)
        }
    }
}

@Composable
private fun BuildDetailsContent(
    state: BuildDetailsState,
    details: BuildDetails,
    onIntent: (BuildDetailsIntent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (details.source == DataSource.SAMPLE) SampleDataBanner()

        SectionCard("Build") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${details.number}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(details.status)
            }
            KeyValueRow("Started", TimeFormat.timestamp(details.startedAtMillis))
            KeyValueRow(
                "Duration",
                if (details.building) {
                    "Running (estimated ${TimeFormat.duration(details.estimatedDurationMillis)})"
                } else {
                    TimeFormat.duration(details.durationMillis)
                },
            )
            if (state.isPolling) {
                Text("Auto-refreshing every few seconds while the build runs.", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("Unit tests") {
            val tests = details.tests
            if (tests == null) {
                Text("No JUnit results were published for this build.", style = MaterialTheme.typography.bodyMedium)
            } else {
                KeyValueRow("Total", tests.total.toString())
                KeyValueRow("Passed", tests.passed.toString())
                KeyValueRow("Failed", tests.failed.toString())
                KeyValueRow("Skipped", tests.skipped.toString())
            }
        }

        SectionCard("Artifacts") {
            if (details.artifacts.isEmpty()) {
                Text("No archived artifacts.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Opens in your browser. Jenkins will ask you to log in there; the app doesn't share its token.",
                    style = MaterialTheme.typography.bodySmall,
                )
                details.artifacts.forEach { artifact ->
                    Text(
                        artifact.relativePath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntent(BuildDetailsIntent.OpenArtifact(artifact)) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }

        SectionCard("Console output") {
            when (val console = state.console) {
                ConsoleState.NotLoaded -> OutlinedButton(onClick = { onIntent(BuildDetailsIntent.LoadConsole) }) {
                    Text("Load console")
                }
                ConsoleState.Loading -> SmallSpinner()
                is ConsoleState.Failed -> {
                    Text(ErrorMessages.title(console.error), color = MaterialTheme.colorScheme.error)
                    Text(ErrorMessages.detail(console.error), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onIntent(BuildDetailsIntent.LoadConsole) }) { Text("Retry") }
                }
                is ConsoleState.Loaded -> {
                    if (console.log.truncated) {
                        Text("Showing the end of a long log.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (console.log.redactions > 0) {
                        Text(
                            "The app masked ${console.log.redactions} secret-looking value(s) that Jenkins didn't mask.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    CodeBlock(console.log.text, Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()))
                }
            }
        }
    }
}
