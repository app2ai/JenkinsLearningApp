package com.example.jenkinslearning.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.jenkinslearning.presentation.navigation.AppNavigation
import com.example.jenkinslearning.presentation.theme.JenkinsLearningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JenkinsLearningTheme {
                AppNavigation()
            }
        }
    }
}
