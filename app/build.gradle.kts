import java.io.StringReader
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android here: AGP 9 compiles Kotlin itself.
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// 1. Configuration inputs: where values come from
// ---------------------------------------------------------------------------
//
// Lookup order for every NON-SECRET setting (first match wins):
//   1. Environment variable          e.g. set by Jenkins:  JENKINS_BASE_URL=...
//   2. Gradle property               e.g. gradlew assembleDebug -PjenkinsBaseUrl=...
//   3. local.properties              developer machine only, ignored by Git
//   4. A safe default                e.g. the emulator's host alias 10.0.2.2
//
// local.properties is meant for machine-specific settings (sdk.dir, a local
// Jenkins URL). It is NOT a secret store: it's plain text on disk, IDEs and
// tools read it, and it's easy to commit by accident.

/** Parsed local.properties, or empty if the file doesn't exist (as on Jenkins). */
val localProperties: Properties = Properties().also { props ->
    val file = rootProject.layout.projectDirectory.file("local.properties")
    // providers.fileContents is configuration-cache friendly and returns no
    // value (instead of throwing) when the file is missing.
    providers.fileContents(file).asText.orNull?.let { text ->
        runCatching { props.load(StringReader(text)) }
            .onFailure { logger.warn("local.properties could not be parsed; ignoring it.") }
    }
}

fun nonSecretSetting(envName: String, gradlePropertyName: String, localKey: String): String? =
    providers.environmentVariable(envName).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty(gradlePropertyName).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: localProperties.getProperty(localKey)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Validates the Jenkins URL. An invalid value logs a warning and falls back to
 * the default instead of failing the build. The rejected value is never logged,
 * because it might contain credentials (http://user:token@host/).
 */
fun sanitizedJenkinsBaseUrl(raw: String?): String {
    val fallback = "http://10.0.2.2:8080/"
    if (raw == null) return fallback
    val uri = runCatching { URI(raw) }.getOrNull()
    val valid = uri != null &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null &&
        uri.rawQuery == null &&
        uri.rawFragment == null
    if (!valid) {
        logger.warn("JENKINS_BASE_URL is not a valid http(s) URL without credentials (value hidden). Using $fallback")
        return fallback
    }
    return if (raw.endsWith("/")) raw else "$raw/"
}

/** Escapes a value so it can be placed inside a Java string literal in BuildConfig. */
fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val jenkinsBaseUrl = sanitizedJenkinsBaseUrl(
    nonSecretSetting(envName = "JENKINS_BASE_URL", gradlePropertyName = "jenkinsBaseUrl", localKey = "JENKINS_BASE_URL"),
)

// Jenkins sets BUILD_NUMBER and GIT_COMMIT for every build. Locally they're
// absent, so the app shows "local". These values are NOT secrets.
val ciBuildNumber: String? = providers.environmentVariable("BUILD_NUMBER").orNull?.takeIf { it.isNotBlank() }
val ciGitCommit: String? = providers.environmentVariable("GIT_COMMIT").orNull?.take(8)?.takeIf { it.isNotBlank() }

// ---------------------------------------------------------------------------
// 2. Android configuration
// ---------------------------------------------------------------------------

android {
    namespace = "com.example.jenkinslearning"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.jenkinslearning"
        minSdk = 26
        targetSdk = 36
        // A common CI pattern: Jenkins' build number becomes the versionCode, so
        // every CI APK can be traced back to the Jenkins build that produced it.
        versionCode = ciBuildNumber?.toIntOrNull() ?: 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONLY non-secret values belong in BuildConfig. Every BuildConfig field is
        // a constant in the APK's bytecode and anyone can extract it.
        buildConfigField("String", "DEFAULT_JENKINS_BASE_URL", jenkinsBaseUrl.asBuildConfigString())
        buildConfigField("String", "CI_BUILD_NUMBER", (ciBuildNumber ?: "local").asBuildConfigString())
        buildConfigField("String", "CI_GIT_COMMIT", (ciGitCommit ?: "local").asBuildConfigString())
    }

    signingConfigs {
        // Release signing is OPTIONAL and only configured when all four values
        // come from the environment, which Jenkins does with withCredentials in
        // the gated "Release APK" stage. Nothing about signing is committed.
        create("release") {
            val keystoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
            val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            val keyAliasValue = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            val keyPasswordValue = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            if (listOf(keystoreFile, keystorePassword, keyAliasValue, keyPasswordValue).all { !it.isNullOrBlank() }) {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds use the auto-generated debug keystore and need no secrets.
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val release = signingConfigs.getByName("release")
            // Without the signing env vars, assembleRelease still works but produces
            // an *unsigned* APK. Release signing never blocks the learning build.
            if (release.storeFile != null) {
                signingConfig = release
            }
        }
    }

    compileOptions {
        // Built-in Kotlin uses this as the Kotlin jvmTarget too.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Jenkins runs lint in its own stage and marks the build UNSTABLE on failure.
        abortOnError = true
        checkReleaseBuilds = false
        xmlReport = true
        htmlReport = true
    }
}

// ---------------------------------------------------------------------------
// 3. A build-time secret, used the safe way
// ---------------------------------------------------------------------------
//
// Imagine a build step that has to call a service with a token (uploading
// mapping files, say). The token is needed *by the build*, not *by the app*, so:
//   - it is read lazily from the environment (Jenkins injects it with withCredentials)
//   - it is used inside the task and never written to BuildConfig, resources,
//     the manifest or any other packaged output
//   - it is marked @Internal so it is not a tracked task input
//   - the task never prints the value
// Reading an environment variable does NOT by itself make a value safe: a build
// script that printed it, or passed it to a command line, would still leak it.

abstract class VerifyCiSecretTask : DefaultTask() {
    @get:Input
    abstract val secretName: Property<String>

    @get:Internal
    abstract val secretValue: Property<String>

    @TaskAction
    fun verify() {
        val name = secretName.get()
        val present = !secretValue.orNull.isNullOrBlank()
        if (!present) {
            throw GradleException(
                "$name is not set. On Jenkins, bind it with withCredentials; locally, export it " +
                    "in your current shell session only. Never add it to gradle.properties or local.properties.",
            )
        }
        // A real integration would use the value here, e.g. as an HTTP header.
        logger.lifecycle("$name is available to the build. The value is intentionally not printed.")
    }
}

tasks.register<VerifyCiSecretTask>("verifySampleToken") {
    group = "ci"
    description = "Shows how a CI-injected secret is consumed by Gradle without being packaged or printed."
    secretName.set("SAMPLE_TOKEN")
    secretValue.set(providers.environmentVariable("SAMPLE_TOKEN"))
}

// ---------------------------------------------------------------------------
// 4. Dependencies
// ---------------------------------------------------------------------------

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver3)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
