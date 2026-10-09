package com.example.jenkinslearning.feature.jenkins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jenkinslearning.R
import com.example.jenkinslearning.core.common.TimeFormat
import com.example.jenkinslearning.domain.model.BuildSummary
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.JobSummary
import com.example.jenkinslearning.presentation.components.AppTopBar
import com.example.jenkinslearning.presentation.components.EmptyState
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

// ------------------------------------------------------------------ Jobs list

@Composable
fun JobsRoute(onOpenJob: (String) -> Unit, viewModel: JobsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is JobsEffect.NavigateToJob -> onOpenJob(effect.jobFullName)
            is JobsEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }
    JobsScreen(state, snackbar, viewModel::onIntent)
}

@Composable
fun JobsScreen(state: JobsState, snackbar: SnackbarHostState, onIntent: (JobsIntent) -> Unit) {
    ScreenScaffold(
        topBar = {
            AppTopBar("Jenkins Jobs", actions = {
                RefreshAction({ onIntent(JobsIntent.Refresh) }, enabled = !state.isLoading)
            })
        },
        snackbarHostState = snackbar,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val error = state.error
        when {
            state.isLoading -> LoadingState("Loading jobs…", modifier)
            error != null -> ErrorState(error, onRetry = { onIntent(JobsIntent.Refresh) }, modifier = modifier)
            state.isEmpty -> EmptyState("No jobs", "This Jenkins has no jobs your user can see.", modifier)
            else -> LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.source == DataSource.SAMPLE) item { SampleDataBanner() }
                items(state.jobs, key = { it.fullName }) { job ->
                    JobRow(
                        job = job,
                        isTriggering = state.triggeringJob == job.fullName,
                        triggerEnabled = state.triggeringJob == null,
                        onOpen = { onIntent(JobsIntent.OpenJob(job.fullName)) },
                        onTrigger = { onIntent(JobsIntent.TriggerBuild(job)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JobRow(
    job: JobSummary,
    isTriggering: Boolean,
    triggerEnabled: Boolean,
    onOpen: () -> Unit,
    onTrigger: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(job.fullName, style = MaterialTheme.typography.titleSmall)
                val last = job.lastBuild
                Text(
                    if (last == null) {
                        "No builds yet"
                    } else {
                        "#${last.number} · ${TimeFormat.duration(last.durationMillis)} · ${TimeFormat.timestamp(last.startedAtMillis)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(job.status)
            }
            Spacer(Modifier.width(8.dp))
            when {
                isTriggering -> SmallSpinner()
                job.buildable -> IconButton(onClick = onTrigger, enabled = triggerEnabled) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = "Build ${job.fullName} now")
                }
            }
        }
    }
}

// --------------------------------------------------------------- Job details

@Composable
fun JobDetailsRoute(
    jobFullName: String,
    onBack: () -> Unit,
    onOpenBuild: (String, Int) -> Unit,
    viewModel: JobDetailsViewModel = koinViewModel(key = "job:$jobFullName") { parametersOf(jobFullName) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is JobDetailsEffect.NavigateToBuild -> onOpenBuild(effect.jobFullName, effect.buildNumber)
            is JobDetailsEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }
    ScreenScaffold(
        topBar = {
            AppTopBar(state.jobFullName, onBack = onBack, actions = {
                RefreshAction({ viewModel.onIntent(JobDetailsIntent.Refresh) }, enabled = !state.isLoading)
            })
        },
        snackbarHostState = snackbar,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val details = state.details
        val error = state.error
        when {
            state.isLoading -> LoadingState(modifier = modifier)
            error != null -> ErrorState(error, onRetry = { viewModel.onIntent(JobDetailsIntent.Refresh) }, modifier = modifier)
            details != null -> JobDetailsContent(details, state.isTriggering, viewModel::onIntent, modifier)
        }
    }
}

@Composable
private fun JobDetailsContent(
    details: JobDetails,
    isTriggering: Boolean,
    onIntent: (JobDetailsIntent) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (details.source == DataSource.SAMPLE) item { SampleDataBanner() }
        item {
            SectionCard("Job") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(details.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusChip(details.status)
                }
                details.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                KeyValueRow("Last success", details.lastSuccessfulBuildNumber?.let { "#$it" } ?: "—")
                KeyValueRow("Last failure", details.lastFailedBuildNumber?.let { "#$it" } ?: "—")
                KeyValueRow("Parameters", if (details.isParameterized) "Yes (defaults are used)" else "None")
                if (details.inQueue) KeyValueRow("Queue", "A build is waiting in the queue")
                Button(
                    onClick = { onIntent(JobDetailsIntent.TriggerBuild) },
                    enabled = details.buildable && !isTriggering,
                ) {
                    Text(
                        when {
                            isTriggering -> "Requesting…"
                            details.buildable -> "Build now"
                            else -> "Not buildable"
                        },
                    )
                }
            }
        }
        item { Text("Recent builds", style = MaterialTheme.typography.titleSmall) }
        if (details.builds.isEmpty()) {
            item { Text("No builds yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(details.builds, key = { it.number }) { build ->
            BuildRow(build) { onIntent(JobDetailsIntent.OpenBuild(build.number)) }
        }
    }
}

@Composable
private fun BuildRow(build: BuildSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#${build.number}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${TimeFormat.timestamp(build.startedAtMillis)} · ${TimeFormat.duration(build.durationMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(build.status)
        }
        HorizontalDivider()
    }
}
