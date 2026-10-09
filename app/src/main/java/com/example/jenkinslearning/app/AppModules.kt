package com.example.jenkinslearning.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.jenkinslearning.BuildConfig
import com.example.jenkinslearning.core.common.SecretRedactor
import com.example.jenkinslearning.core.config.JenkinsUrlParser
import com.example.jenkinslearning.core.config.UrlParseResult
import com.example.jenkinslearning.core.network.HttpClientFactory
import com.example.jenkinslearning.data.local.DataStoreConnectionSettingsRepository
import com.example.jenkinslearning.data.local.InMemorySessionRepository
import com.example.jenkinslearning.data.remote.JenkinsApiProvider
import com.example.jenkinslearning.data.repository.ModeAwareJenkinsRepository
import com.example.jenkinslearning.data.repository.RemoteJenkinsRepository
import com.example.jenkinslearning.data.repository.SampleJenkinsRepository
import com.example.jenkinslearning.data.repository.StaticLearningContentRepository
import com.example.jenkinslearning.domain.model.AppBuildInfo
import com.example.jenkinslearning.domain.repository.ConnectionSettingsRepository
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import com.example.jenkinslearning.domain.repository.LearningContentRepository
import com.example.jenkinslearning.domain.repository.SessionRepository
import com.example.jenkinslearning.domain.usecase.ClearSessionUseCase
import com.example.jenkinslearning.domain.usecase.GetBuildDetailsUseCase
import com.example.jenkinslearning.domain.usecase.GetConsoleLogUseCase
import com.example.jenkinslearning.domain.usecase.GetCredentialGuideUseCase
import com.example.jenkinslearning.domain.usecase.GetDashboardUseCase
import com.example.jenkinslearning.domain.usecase.GetJobDetailsUseCase
import com.example.jenkinslearning.domain.usecase.GetJobsUseCase
import com.example.jenkinslearning.domain.usecase.GetLearningTopicsUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.domain.usecase.ObserveSessionStateUseCase
import com.example.jenkinslearning.domain.usecase.SaveConnectionSettingsUseCase
import com.example.jenkinslearning.domain.usecase.TriggerBuildUseCase
import com.example.jenkinslearning.feature.build.BuildDetailsViewModel
import com.example.jenkinslearning.feature.credentials.CredentialsViewModel
import com.example.jenkinslearning.feature.dashboard.DashboardViewModel
import com.example.jenkinslearning.feature.jenkins.JobDetailsViewModel
import com.example.jenkinslearning.feature.jenkins.JobsViewModel
import com.example.jenkinslearning.feature.learning.LearningViewModel
import com.example.jenkinslearning.feature.settings.SettingsViewModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection_settings")

private val IO_DISPATCHER = named("io")
private val LIVE_REPOSITORY = named("live")
private val SAMPLE_REPOSITORY = named("sample")

/** BuildConfig values, validated. A bad build-time URL falls back to the emulator default. */
val configModule = module {
    single {
        val parsed = JenkinsUrlParser.parse(BuildConfig.DEFAULT_JENKINS_BASE_URL)
        AppBuildInfo(
            defaultJenkinsBaseUrl = (parsed as? UrlParseResult.Valid)?.baseUrl ?: "http://10.0.2.2:8080/",
            ciBuildNumber = BuildConfig.CI_BUILD_NUMBER,
            ciGitCommit = BuildConfig.CI_GIT_COMMIT,
            versionName = BuildConfig.VERSION_NAME,
            isDebugBuild = BuildConfig.DEBUG,
        )
    }
    single<CoroutineDispatcher>(IO_DISPATCHER) { Dispatchers.IO }
}

val networkModule = module {
    single<Gson> { GsonBuilder().create() }
    single { HttpClientFactory.create(enableLogging = BuildConfig.DEBUG) }
    single { JenkinsApiProvider(baseClient = get(), gson = get(), sessionRepository = get()) }
    // Masks the user's own token if it ever shows up in a console log.
    single {
        val session: SessionRepository = get()
        SecretRedactor(knownSecrets = { listOfNotNull(session.current()?.apiToken) })
    }
}

val dataModule = module {
    single<SessionRepository> { InMemorySessionRepository() }
    single<ConnectionSettingsRepository> {
        DataStoreConnectionSettingsRepository(
            dataStore = androidContext().settingsDataStore,
            defaultBaseUrl = get<AppBuildInfo>().defaultJenkinsBaseUrl,
        )
    }
    single<JenkinsRepository>(LIVE_REPOSITORY) {
        RemoteJenkinsRepository(
            apiProvider = get(),
            settingsRepository = get(),
            redactor = get(),
            ioDispatcher = get(IO_DISPATCHER),
        )
    }
    single<JenkinsRepository>(SAMPLE_REPOSITORY) { SampleJenkinsRepository(redactor = get()) }
    single<JenkinsRepository> {
        ModeAwareJenkinsRepository(
            settingsRepository = get(),
            live = get(LIVE_REPOSITORY),
            sample = get(SAMPLE_REPOSITORY),
        )
    }
    single { StaticLearningContentRepository() } bind LearningContentRepository::class
}

val domainModule = module {
    factoryOf(::GetDashboardUseCase)
    factoryOf(::GetJobsUseCase)
    factoryOf(::GetJobDetailsUseCase)
    factoryOf(::GetBuildDetailsUseCase)
    factoryOf(::GetConsoleLogUseCase)
    factoryOf(::TriggerBuildUseCase)
    factoryOf(::ObserveConnectionSettingsUseCase)
    factoryOf(::SaveConnectionSettingsUseCase)
    factoryOf(::ClearSessionUseCase)
    factoryOf(::ObserveSessionStateUseCase)
    factoryOf(::GetCredentialGuideUseCase)
    factoryOf(::GetLearningTopicsUseCase)
}

val presentationModule = module {
    viewModelOf(::DashboardViewModel)
    viewModelOf(::JobsViewModel)
    viewModelOf(::CredentialsViewModel)
    viewModelOf(::LearningViewModel)
    viewModelOf(::SettingsViewModel)
    // Screen arguments arrive through parametersOf(...) from the navigation route.
    viewModel { params -> JobDetailsViewModel(jobFullName = params.get(), getJobDetails = get(), triggerBuild = get()) }
    viewModel { params ->
        BuildDetailsViewModel(
            jobFullName = params.get(),
            buildNumber = params.get(),
            getBuildDetails = get(),
            getConsoleLog = get(),
        )
    }
}

val appModules = listOf(configModule, networkModule, dataModule, domainModule, presentationModule)
