package com.example.jenkinslearning.data.repository

import com.example.jenkinslearning.domain.model.ConfigConcept
import com.example.jenkinslearning.domain.model.CredentialGuideEntry
import com.example.jenkinslearning.domain.model.CredentialKind
import com.example.jenkinslearning.domain.model.CredentialScope
import com.example.jenkinslearning.domain.model.LearningTopic
import com.example.jenkinslearning.domain.repository.LearningContentRepository

/**
 * Educational content. The credential IDs, stage names and Gradle tasks here
 * match this project's Jenkinsfile and app/build.gradle.kts exactly, so what you
 * read in the app is what the pipeline actually does.
 */
class StaticLearningContentRepository : LearningContentRepository {

    override fun credentialGuide(): List<CredentialGuideEntry> = listOf(
        CredentialGuideEntry(
            id = "sample-api-token",
            kind = CredentialKind.SECRET_TEXT,
            scope = CredentialScope.GLOBAL,
            purpose = "Stands in for a token a build step needs (e.g. uploading mapping files). Used only by the build, never packaged into the APK.",
            configuredAt = "Manage Jenkins → Credentials → System → Global credentials (unrestricted) → Add Credentials",
            consumedBy = "Stage \"Credentials demo\" binds it to the SAMPLE_TOKEN environment variable; Gradle task :app:verifySampleToken reads it with providers.environmentVariable().",
            pipelineSnippet = """
                withCredentials([string(credentialsId: 'sample-api-token', variable: 'SAMPLE_TOKEN')]) {
                    bat 'gradlew.bat --no-daemon :app:verifySampleToken'
                }
            """.trimIndent(),
        ),
        CredentialGuideEntry(
            id = "sample-config-file",
            kind = CredentialKind.SECRET_FILE,
            scope = CredentialScope.FOLDER,
            purpose = "A whole file treated as a secret (e.g. a service-account JSON). Shows folder scope: only jobs inside that folder can use it.",
            configuredAt = "Open the folder → Credentials → (folder store) → Global credentials → Add Credentials",
            consumedBy = "Stage \"Credentials demo\" binds it with file(...). Jenkins writes it to a temporary path and puts that PATH (not the content) in SAMPLE_CONFIG_FILE.",
            pipelineSnippet = """
                withCredentials([file(credentialsId: 'sample-config-file', variable: 'SAMPLE_CONFIG_FILE')]) {
                    bat 'if exist "%SAMPLE_CONFIG_FILE%" (echo Secret file is available) else (exit /b 1)'
                }
            """.trimIndent(),
        ),
        CredentialGuideEntry(
            id = "android-release-keystore",
            kind = CredentialKind.SECRET_FILE,
            scope = CredentialScope.GLOBAL,
            purpose = "The release signing keystore (.jks). Never committed to Git and never shipped in the app.",
            configuredAt = "Global credentials (or a folder store) → Add Credentials → Kind: Secret file → upload the .jks",
            consumedBy = "Optional stage \"Release APK\" exposes it as ANDROID_KEYSTORE_FILE; app/build.gradle.kts signingConfigs.release reads that path.",
            pipelineSnippet = """
                file(credentialsId: 'android-release-keystore', variable: 'ANDROID_KEYSTORE_FILE')
            """.trimIndent(),
        ),
        CredentialGuideEntry(
            id = "android-keystore-password",
            kind = CredentialKind.SECRET_TEXT,
            scope = CredentialScope.GLOBAL,
            purpose = "Password for the release keystore file.",
            configuredAt = "Global credentials → Add Credentials → Kind: Secret text",
            consumedBy = "Stage \"Release APK\" → ANDROID_KEYSTORE_PASSWORD → signingConfigs.release.storePassword",
            pipelineSnippet = """
                string(credentialsId: 'android-keystore-password', variable: 'ANDROID_KEYSTORE_PASSWORD')
            """.trimIndent(),
        ),
        CredentialGuideEntry(
            id = "android-release-key",
            kind = CredentialKind.USERNAME_PASSWORD,
            scope = CredentialScope.GLOBAL,
            purpose = "Key alias (as the username) and key password, stored together as one Username with password credential.",
            configuredAt = "Global credentials → Add Credentials → Kind: Username with password",
            consumedBy = "Stage \"Release APK\" → ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD → signingConfigs.release",
            pipelineSnippet = """
                usernamePassword(credentialsId: 'android-release-key',
                                 usernameVariable: 'ANDROID_KEY_ALIAS',
                                 passwordVariable: 'ANDROID_KEY_PASSWORD')
            """.trimIndent(),
        ),
        CredentialGuideEntry(
            id = "git-checkout",
            kind = CredentialKind.USERNAME_PASSWORD,
            scope = CredentialScope.GLOBAL,
            purpose = "Lets Jenkins clone a private repository (GitHub username + personal access token, or use an SSH key credential instead).",
            configuredAt = "Global credentials → Username with password (or SSH username with private key)",
            consumedBy = "The pipeline job's \"Pipeline script from SCM\" settings, not the Jenkinsfile. checkout scm reuses it.",
            pipelineSnippet = """
                // Job configuration → Pipeline → Definition: Pipeline script from SCM
                //   Repository URL: https://github.com/<you>/JenkinsLearningApp.git
                //   Credentials:    git-checkout
                checkout scm
            """.trimIndent(),
        ),
    )

    override fun configConcepts(): List<ConfigConcept> = listOf(
        ConfigConcept(
            title = "Jenkins global credentials",
            whereItLives = "Jenkins' encrypted credential store (JENKINS_HOME), in the Global domain.",
            whoReadsIt = "Any job on this Jenkins that names the credential ID in withCredentials.",
            safeForSecrets = "Yes. This is where CI secrets belong.",
            example = "string(credentialsId: 'sample-api-token', variable: 'SAMPLE_TOKEN')",
        ),
        ConfigConcept(
            title = "Folder-scoped credentials",
            whereItLives = "The credential store of one Jenkins folder.",
            whoReadsIt = "Only jobs inside that folder (and its sub-folders).",
            safeForSecrets = "Yes, with a smaller blast radius than global. Prefer it when only some jobs need the secret.",
            example = "file(credentialsId: 'sample-config-file', variable: 'SAMPLE_CONFIG_FILE')",
        ),
        ConfigConcept(
            title = "local.properties",
            whereItLives = "Project root on each developer's machine. Ignored by Git; doesn't exist on Jenkins.",
            whoReadsIt = "Android Studio and Gradle on that machine (sdk.dir, and this project's JENKINS_BASE_URL).",
            safeForSecrets = "No. Plain text, easy to commit by mistake. Use it for machine-specific, non-secret settings.",
            example = "sdk.dir=C\\:\\\\Users\\\\you\\\\AppData\\\\Local\\\\Android\\\\Sdk\nJENKINS_BASE_URL=http://10.0.2.2:8080/",
        ),
        ConfigConcept(
            title = "Environment variables & build parameters",
            whereItLives = "The build process environment: node settings, environment {}, parameters {}, withCredentials.",
            whoReadsIt = "Gradle via providers.environmentVariable(...) and any command the build runs.",
            safeForSecrets = "Only when injected by withCredentials and never printed. Plain environment {} values and build parameters are visible in the job UI and logs.",
            example = "providers.environmentVariable(\"SAMPLE_TOKEN\")",
        ),
        ConfigConcept(
            title = "BuildConfig & generated resources",
            whereItLives = "Compiled into the APK (classes.dex / resources.arsc).",
            whoReadsIt = "The app at runtime, and anyone who downloads the APK and decompiles it.",
            safeForSecrets = "Never. Use it for public values like the default Jenkins URL or the CI build number.",
            example = "buildConfigField(\"String\", \"CI_BUILD_NUMBER\", \"\\\"42\\\"\")",
        ),
        ConfigConcept(
            title = "Runtime secrets vs build-time secrets",
            whereItLives = "Build-time: Jenkins credentials, used during the build only. Runtime: entered or fetched by the app after install.",
            whoReadsIt = "Build-time: Gradle tasks on the CI agent. Runtime: the app, for the current user.",
            safeForSecrets = "Keep build-time secrets out of the APK entirely. This app keeps its runtime Jenkins token in memory; Android Keystore is the option for persistence.",
            example = "SAMPLE_TOKEN (build-time) vs. your Jenkins API token typed in Settings (runtime)",
        ),
    )

    override fun learningTopics(): List<LearningTopic> = listOf(
        LearningTopic(
            id = "lifecycle",
            title = "Jenkins pipeline lifecycle",
            summary = "From a commit to an archived APK.",
            body = "1) A build is triggered (manually, by polling, or by a webhook). 2) Jenkins reads the Jenkinsfile from Git. " +
                "3) It allocates an agent and workspace, then checks out the code (checkout scm). 4) Stages run in order; each step's " +
                "exit code decides success. 5) post { } blocks always run, which is where test reports are published and APKs archived. " +
                "6) The result (SUCCESS, UNSTABLE, FAILURE, ABORTED) is recorded with the build number.",
            codeSample = null,
        ),
        LearningTopic(
            id = "jenkinsfile",
            title = "Jenkinsfile syntax",
            summary = "Declarative pipeline building blocks.",
            body = "pipeline { } wraps everything. agent picks where it runs. options, parameters and environment configure the run. " +
                "stages { stage('Name') { steps { ... } } } hold the work. when { } makes a stage conditional. post { always/success/failure } " +
                "runs at the end. Use single-quoted strings for commands that contain secrets, so Groovy doesn't interpolate them.",
            codeSample = """
                stage('Unit tests') {
                    steps {
                        bat 'gradlew.bat --no-daemon --stacktrace :app:testDebugUnitTest'
                    }
                }
            """.trimIndent(),
        ),
        LearningTopic(
            id = "gradle-tasks",
            title = "Gradle tasks used in Android CI",
            summary = "What each pipeline stage calls.",
            body = ":app:compileDebugSources compiles quickly as an early check. :app:testDebugUnitTest runs JVM unit tests and writes JUnit XML. " +
                ":app:lintDebug runs Android Lint. :app:assembleDebug builds app-debug.apk. :app:assembleRelease builds the release APK " +
                "(signed only when signing env vars exist). --no-daemon avoids leftover Gradle daemons on CI agents; --stacktrace makes failures readable.",
            codeSample = "gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug",
        ),
        LearningTopic(
            id = "debug-release",
            title = "Debug vs release builds",
            summary = "Signing, minification and network rules differ.",
            body = "Debug builds are signed with an auto-generated debug key, are debuggable, and (in this project) may use cleartext HTTP " +
                "to 10.0.2.2/localhost only. Release builds are minified with R8, block cleartext entirely, and need a real signing key, " +
                "which only Jenkins holds as credentials. Release signing is optional here and gated behind the BUILD_RELEASE parameter.",
            codeSample = null,
        ),
        LearningTopic(
            id = "tests",
            title = "Unit tests vs instrumentation tests",
            summary = "JVM tests run anywhere; device tests need an emulator.",
            body = "Unit tests (src/test) run on the JVM, need no emulator, and are what Jenkins runs here. Instrumentation tests " +
                "(src/androidTest, connectedDebugAndroidTest) run on a device or emulator. On CI they need an emulator started headless " +
                "(or a device farm). Results land in app/build/test-results as JUnit XML, which the junit step publishes.",
            codeSample = "junit testResults: 'app/build/test-results/testDebugUnitTest/*.xml', allowEmptyResults: true",
        ),
        LearningTopic(
            id = "binding",
            title = "Credential binding",
            summary = "withCredentials exposes a secret only inside its block.",
            body = "withCredentials looks up a credential by ID, puts it in an environment variable for the steps inside the block, and " +
                "masks the exact value in the console. Masking is a safety net, not protection: a secret that is base64-encoded, split, " +
                "written to a file and archived, or printed by a tool in another form isn't masked. Other processes on the agent " +
                "might see command-line arguments, so pass secrets through environment variables or files, not arguments.",
            codeSample = """
                withCredentials([string(credentialsId: 'sample-api-token', variable: 'SAMPLE_TOKEN')]) {
                    // Single quotes: the Windows shell expands %SAMPLE_TOKEN%, not Groovy.
                    bat 'gradlew.bat --no-daemon :app:verifySampleToken'
                }
            """.trimIndent(),
        ),
        LearningTopic(
            id = "env",
            title = "Environment variables",
            summary = "Jenkins-provided, node-level and pipeline-level.",
            body = "Jenkins provides BUILD_NUMBER, JOB_NAME, WORKSPACE and GIT_COMMIT. Node settings provide machine paths like " +
                "ANDROID_HOME and JAVA_HOME. This app's build reads BUILD_NUMBER and GIT_COMMIT into BuildConfig (both public), " +
                "so the APK built by Jenkins shows its own build number on the Settings screen.",
            codeSample = "val ciBuildNumber = providers.environmentVariable(\"BUILD_NUMBER\").orNull",
        ),
        LearningTopic(
            id = "local-properties",
            title = "local.properties",
            summary = "Per-machine settings that never reach Git or Jenkins.",
            body = "Android Studio writes sdk.dir here. This project also reads JENKINS_BASE_URL from it (non-secret). On Jenkins the file " +
                "doesn't exist, so Gradle finds the SDK through ANDROID_HOME and the URL through an env var or default. " +
                "Copy local.properties.example to start.",
            codeSample = "JENKINS_BASE_URL=http://10.0.2.2:8080/",
        ),
        LearningTopic(
            id = "archiving",
            title = "APK artifact archiving",
            summary = "Keep build outputs attached to the build.",
            body = "archiveArtifacts copies matching files from the workspace into Jenkins' build record, so they survive the next build " +
                "and appear as download links on the build page. fingerprint: true records checksums so you can trace which build produced an APK.",
            codeSample = "archiveArtifacts artifacts: 'app/build/outputs/apk/**/*.apk', fingerprint: true, allowEmptyArchive: true",
        ),
        LearningTopic(
            id = "troubleshooting",
            title = "CI build failures and troubleshooting",
            summary = "The usual suspects.",
            body = "SDK location not found → set ANDROID_HOME on the Jenkins node. Unsupported class file / JDK mismatch → point JAVA_HOME at " +
                "JDK 17+. 'gradlew.bat' is not recognized → the workspace isn't the repo root or checkout failed. License not accepted → run " +
                "sdkmanager --licenses as the Jenkins service user. Tests not found by junit → check the XML path. 401/403 from the app → " +
                "wrong username/API token or missing permissions. See docs/06-troubleshooting.md for more.",
            codeSample = null,
        ),
    )
}
