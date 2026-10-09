package com.example.jenkinslearning.feature.dashboard

import androidx.lifecycle.viewModelScope
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.usecase.GetDashboardUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val getDashboard: GetDashboardUseCase,
    observeSettings: ObserveConnectionSettingsUseCase,
) : MviViewModel<DashboardIntent, DashboardState, DashboardEffect>(DashboardState()) {

    private var loadJob: Job? = null

    init {
        // Loads on start, and again whenever the URL/username/mode changes in Settings.
        viewModelScope.launch {
            observeSettings().distinctUntilChanged().collect { load(isRefresh = false) }
        }
    }

    override fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Refresh -> load(isRefresh = true)
            DashboardIntent.OpenSettings -> sendEffect(DashboardEffect.NavigateToSettings)
            is DashboardIntent.OpenJob -> sendEffect(DashboardEffect.NavigateToJob(intent.jobFullName))
        }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel() // A newer request replaces an older one.
        val hasData = currentState.summary != null
        setState {
            copy(
                isLoading = !(isRefresh && hasData),
                isRefreshing = isRefresh && hasData,
                summary = if (isRefresh) summary else null,
                error = null,
            )
        }
        loadJob = viewModelScope.launch {
            when (val result = getDashboard()) {
                is Outcome.Success -> setState {
                    copy(isLoading = false, isRefreshing = false, summary = result.value, error = null)
                }
                is Outcome.Failure -> {
                    val keepData = isRefresh && currentState.summary != null
                    setState {
                        copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (keepData) null else result.error,
                        )
                    }
                    if (keepData) sendEffect(DashboardEffect.ShowMessage(ErrorMessages.title(result.error)))
                }
            }
        }
    }
}
