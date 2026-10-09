package com.example.jenkinslearning.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.presentation.components.AppTopBar
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.components.KeyValueRow
import com.example.jenkinslearning.presentation.components.ScreenScaffold
import com.example.jenkinslearning.presentation.components.SectionCard
import com.example.jenkinslearning.presentation.components.SmallSpinner
import com.example.jenkinslearning.presentation.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // The token is held only in this composable's memory while typing.
    // remember (not rememberSaveable): rememberSaveable would write it into the
    // saved-instance-state Bundle, which Android can persist to disk.
    var token by remember { mutableStateOf("") }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.ClearTokenField -> token = ""
            is SettingsEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
        }
    }

    ScreenScaffold(topBar = { AppTopBar("Build Configuration") }, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeCard(state.mode) { viewModel.onIntent(SettingsIntent.ModeChanged(it)) }

            SectionCard("Jenkins server") {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { viewModel.onIntent(SettingsIntent.BaseUrlChanged(it)) },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://10.0.2.2:8080/") },
                    singleLine = true,
                    isError = state.urlError != null,
                    supportingText = {
                        Text(state.urlError ?: "Emulator → 10.0.2.2 means your PC. Phone → use adb reverse or your PC's LAN IP.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isCleartextUrl) {
                    Text(
                        "http:// sends your token unencrypted. Debug builds allow it only for 10.0.2.2, 127.0.0.1 and localhost; release builds block it. Prefer https://.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { viewModel.onIntent(SettingsIntent.UsernameChanged(it)) },
                    label = { Text("Jenkins username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (state.hasToken) "API token (set; leave blank to keep)" else "API token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Kept in memory only. Forgotten when the app process ends.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.onIntent(SettingsIntent.Save(token)) }, enabled = !state.isSaving) {
                        Text(if (state.mode == DataMode.LIVE) "Save & test" else "Save")
                    }
                    OutlinedButton(onClick = { viewModel.onIntent(SettingsIntent.TestConnection) }, enabled = !state.isTesting) {
                        Text("Test")
                    }
                    if (state.hasToken) {
                        TextButton(onClick = { viewModel.onIntent(SettingsIntent.ClearToken) }) { Text("Forget token") }
                    }
                }
                Text(
                    "Test uses the saved settings. Create a token in Jenkins: your user menu → Security → API Token → Add new token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TestResultCard(state)

            SectionCard("This APK was built by") {
                val info = state.buildInfo
                KeyValueRow("Built by", if (info.builtByCi) "Jenkins (CI)" else "A developer machine")
                KeyValueRow("CI build #", info.ciBuildNumber)
                KeyValueRow("Git commit", info.ciGitCommit)
                KeyValueRow("Version", info.versionName)
                KeyValueRow("Build type", if (info.isDebugBuild) "debug" else "release")
                KeyValueRow("Default URL", info.defaultJenkinsBaseUrl)
                Text(
                    "These values come from BuildConfig. Jenkins' BUILD_NUMBER and GIT_COMMIT environment variables are read by " +
                        "app/build.gradle.kts; locally they're \"local\". All public, which is why they're allowed in BuildConfig.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeCard(selected: DataMode, onSelect: (DataMode) -> Unit) {
    SectionCard("Data source") {
        Column(Modifier.selectableGroup()) {
            ModeOption("Sample data (no network)", selected == DataMode.SAMPLE) { onSelect(DataMode.SAMPLE) }
            ModeOption("Live Jenkins (REST API)", selected == DataMode.LIVE) { onSelect(DataMode.LIVE) }
        }
        Text("Changes apply when you tap Save.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TestResultCard(state: SettingsState) {
    if (!state.isTesting && state.testResult == null) return
    SectionCard("Connection test") {
        when (val result = state.testResult) {
            null -> SmallSpinner()
            is ConnectionTestResult.Success -> {
                Text(
                    if (result.source == DataSource.SAMPLE) "Sample mode: no server was contacted." else "Connected to Jenkins.",
                    color = MaterialTheme.colorScheme.secondary,
                )
                KeyValueRow("Version", result.jenkinsVersion ?: "Not reported")
                KeyValueRow("Jobs", result.jobCount.toString())
            }
            is ConnectionTestResult.Failure -> {
                Text(ErrorMessages.title(result.error), color = MaterialTheme.colorScheme.error)
                Text(ErrorMessages.detail(result.error), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
