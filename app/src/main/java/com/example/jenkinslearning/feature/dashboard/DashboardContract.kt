package com.example.jenkinslearning.feature.dashboard

import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.DashboardSummary
import com.example.jenkinslearning.domain.model.DataSource

sealed interface DashboardIntent {
    data object Refresh : DashboardIntent
    data object OpenSettings : DashboardIntent
    data class OpenJob(val jobFullName: String) : DashboardIntent
}

data class DashboardState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val summary: DashboardSummary? = null,
    /** Only set when there's no data to show. Refresh failures with data become a snackbar. */
    val error: JenkinsError? = null,
) {
    val isEmpty: Boolean get() = summary != null && summary.jobCount == 0

    val connection: ConnectionStatus
        get() = when {
            summary?.overview?.source == DataSource.SAMPLE -> ConnectionStatus.SAMPLE
            summary != null -> ConnectionStatus.CONNECTED
            error != null -> ConnectionStatus.FAILED
            else -> ConnectionStatus.CONNECTING
        }
}

enum class ConnectionStatus { CONNECTING, CONNECTED, SAMPLE, FAILED }

sealed interface DashboardEffect {
    data object NavigateToSettings : DashboardEffect
    data class NavigateToJob(val jobFullName: String) : DashboardEffect
    data class ShowMessage(val message: String) : DashboardEffect
}
