package com.example.jenkinslearning.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jenkinslearning.core.common.TimeFormat
import com.example.jenkinslearning.domain.model.DashboardSummary
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobBuild
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

@Composable
fun DashboardRoute(
    onOpenSettings: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            DashboardEffect.NavigateToSettings -> onOpenSettings()
            is DashboardEffect.NavigateToJob -> onOpenJob(effect.jobFullName)
            is DashboardEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }
    DashboardScreen(state = state, snackbar = snackbar, onIntent = viewModel::onIntent)
}

@Composable
fun DashboardScreen(state: DashboardState, snackbar: SnackbarHostState, onIntent: (DashboardIntent) -> Unit) {
    ScreenScaffold(
        topBar = {
            AppTopBar(title = "Jenkins Dashboard", actions = {
                if (state.isRefreshing) SmallSpinner()
                RefreshAction(onRefresh = { onIntent(DashboardIntent.Refresh) }, enabled = !state.isLoading)
            })
        },
        snackbarHostState = snackbar,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val summary = state.summary
        val error = state.error
        when {
            state.isLoading -> LoadingState("Contacting Jenkins…", modifier)
            error != null -> ErrorState(
                error = error,
                onRetry = { onIntent(DashboardIntent.Refresh) },
                onOpenSettings = { onIntent(DashboardIntent.OpenSettings) },
                modifier = modifier,
            )
            summary != null -> DashboardContent(state, summary, onIntent, modifier)
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    summary: DashboardSummary,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary.overview.source == DataSource.SAMPLE) SampleDataBanner()

        SectionCard(title = "Connection") {
            KeyValueRow("Status", connectionLabel(state.connection))
            KeyValueRow("Server", summary.overview.baseUrl)
            KeyValueRow("Jenkins version", summary.overview.jenkinsVersion ?: "Not reported")
            KeyValueRow("Jobs", summary.jobCount.toString())
        }

        if (state.isEmpty) {
            EmptyState(
                title = "No jobs yet",
                message = "Create a Pipeline job in Jenkins that uses this repository's Jenkinsfile. See docs/03-jenkins-setup-windows.md.",
            )
        } else {
            BuildCard("Last build", summary.latestBuild, onIntent)
            BuildCard("Last successful build", summary.latestSuccessfulBuild, onIntent)
        }
    }
}

@Composable
private fun BuildCard(title: String, jobBuild: JobBuild?, onIntent: (DashboardIntent) -> Unit) {
    SectionCard(
        title = title,
        modifier = if (jobBuild != null) {
            Modifier.clickable { onIntent(DashboardIntent.OpenJob(jobBuild.job.fullName)) }
        } else {
            Modifier
        },
    ) {
        if (jobBuild == null) {
            Text("No builds recorded.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${jobBuild.job.fullName}  #${jobBuild.build.number}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            StatusChip(jobBuild.build.status)
        }
        KeyValueRow("Started", TimeFormat.timestamp(jobBuild.build.startedAtMillis))
        KeyValueRow("Duration", TimeFormat.duration(jobBuild.build.durationMillis))
    }
}

private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTING -> "Connecting…"
    ConnectionStatus.CONNECTED -> "Connected"
    ConnectionStatus.SAMPLE -> "Sample mode (offline)"
    ConnectionStatus.FAILED -> "Not connected"
}
