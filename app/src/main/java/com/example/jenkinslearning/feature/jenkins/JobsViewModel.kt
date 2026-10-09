package com.example.jenkinslearning.feature.jenkins

import androidx.lifecycle.viewModelScope
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.usecase.GetJobsUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.domain.usecase.TriggerBuildUseCase
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class JobsViewModel(
    private val getJobs: GetJobsUseCase,
    private val triggerBuild: TriggerBuildUseCase,
    observeSettings: ObserveConnectionSettingsUseCase,
) : MviViewModel<JobsIntent, JobsState, JobsEffect>(JobsState()) {

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            observeSettings().distinctUntilChanged().collect { load() }
        }
    }

    override fun handleIntent(intent: JobsIntent) {
        when (intent) {
            JobsIntent.Refresh -> load()
            is JobsIntent.OpenJob -> sendEffect(JobsEffect.NavigateToJob(intent.jobFullName))
            is JobsIntent.TriggerBuild -> trigger(intent)
        }
    }

    private fun load() {
        loadJob?.cancel()
        setState { copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = getJobs()) {
                is Outcome.Success -> setState {
                    copy(isLoading = false, jobs = result.value.jobs, source = result.value.source, error = null)
                }
                is Outcome.Failure -> setState { copy(isLoading = false, jobs = emptyList(), error = result.error) }
            }
        }
    }

    private fun trigger(intent: JobsIntent.TriggerBuild) {
        if (currentState.triggeringJob != null) return // One trigger at a time; prevents double taps.
        setState { copy(triggeringJob = intent.job.fullName) }
        viewModelScope.launch {
            val result = triggerBuild(intent.job)
            setState { copy(triggeringJob = null) }
            when (result) {
                is Outcome.Success -> {
                    sendEffect(JobsEffect.ShowMessage(queuedMessage(result.value)))
                    load()
                }
                is Outcome.Failure -> sendEffect(
                    JobsEffect.ShowMessage("${ErrorMessages.title(result.error)}: ${ErrorMessages.detail(result.error)}"),
                )
            }
        }
    }
}
