package com.example.jenkinslearning.feature.jenkins

import androidx.lifecycle.viewModelScope
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.usecase.GetJobDetailsUseCase
import com.example.jenkinslearning.domain.usecase.TriggerBuildUseCase
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** [jobFullName] comes from the navigation argument (passed via Koin parametersOf). */
class JobDetailsViewModel(
    jobFullName: String,
    private val getJobDetails: GetJobDetailsUseCase,
    private val triggerBuild: TriggerBuildUseCase,
) : MviViewModel<JobDetailsIntent, JobDetailsState, JobDetailsEffect>(JobDetailsState(jobFullName = jobFullName)) {

    private var loadJob: Job? = null

    init {
        load()
    }

    override fun handleIntent(intent: JobDetailsIntent) {
        when (intent) {
            JobDetailsIntent.Refresh -> load()
            JobDetailsIntent.TriggerBuild -> trigger()
            is JobDetailsIntent.OpenBuild ->
                sendEffect(JobDetailsEffect.NavigateToBuild(currentState.jobFullName, intent.buildNumber))
        }
    }

    private fun load() {
        loadJob?.cancel()
        setState { copy(isLoading = details == null, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = getJobDetails(currentState.jobFullName)) {
                is Outcome.Success -> setState { copy(isLoading = false, details = result.value, error = null) }
                is Outcome.Failure -> {
                    if (currentState.details == null) {
                        setState { copy(isLoading = false, error = result.error) }
                    } else {
                        setState { copy(isLoading = false) }
                        sendEffect(JobDetailsEffect.ShowMessage(ErrorMessages.title(result.error)))
                    }
                }
            }
        }
    }

    private fun trigger() {
        val details = currentState.details ?: return
        if (currentState.isTriggering) return
        setState { copy(isTriggering = true) }
        viewModelScope.launch {
            val result = triggerBuild(details)
            setState { copy(isTriggering = false) }
            when (result) {
                is Outcome.Success -> {
                    sendEffect(JobDetailsEffect.ShowMessage(queuedMessage(result.value)))
                    load()
                }
                is Outcome.Failure -> sendEffect(
                    JobDetailsEffect.ShowMessage("${ErrorMessages.title(result.error)}: ${ErrorMessages.detail(result.error)}"),
                )
            }
        }
    }
}
