// Top-level build file. Module configuration lives in app/build.gradle.kts.

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 uses built-in Kotlin and brings its own minimum Kotlin Gradle Plugin.
        // Declaring KGP here (the approach documented in the AGP 9 release notes)
        // makes the Kotlin compiler version match the Compose compiler plugin below.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
