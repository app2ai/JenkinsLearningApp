package com.example.jenkinslearning.feature.dashboard

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.DashboardSummary
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.presentation.theme.JenkinsLearningTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation (device/emulator) test: run with
 *   gradlew.bat :app:connectedDebugAndroidTest
 * Not part of the default Jenkins pipeline, which has no emulator.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sampleDataIsAlwaysLabeled() {
        val summary = DashboardSummary(
            overview = ServerOverview("sample://jenkins", "sample", emptyList(), DataSource.SAMPLE),
            jobCount = 0,
            latestBuild = null,
            latestSuccessfulBuild = null,
        )
        compose.setContent {
            JenkinsLearningTheme {
                DashboardScreen(DashboardState(isLoading = false, summary = summary), SnackbarHostState()) {}
            }
        }
        compose.onNodeWithText("Sample data", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sample mode (offline)").assertIsDisplayed()
    }

    @Test
    fun errorStateOffersRetry() {
        val intents = mutableListOf<DashboardIntent>()
        compose.setContent {
            JenkinsLearningTheme {
                DashboardScreen(
                    DashboardState(isLoading = false, error = JenkinsError.Unauthorized),
                    SnackbarHostState(),
                ) { intents += it }
            }
        }
        compose.onNodeWithText("Authentication failed (401)").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf<DashboardIntent>(DashboardIntent.Refresh), intents)
    }
}
