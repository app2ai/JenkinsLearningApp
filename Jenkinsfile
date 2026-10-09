// =============================================================================
// JenkinsLearningApp: Declarative pipeline for a WINDOWS Jenkins agent (bat steps)
// Linux/macOS agent variant: ci/linux/Jenkinsfile
// Walkthrough of every stage: docs/05-pipeline-walkthrough.md
//
// Prerequisites on the Jenkins node (see docs/03-jenkins-setup-windows.md):
//   - JDK 21 or 25 (JAVA_HOME), Git for Windows, Android SDK (ANDROID_HOME)
//   - Plugins: Pipeline, Git, Credentials Binding, JUnit (all in "suggested plugins")
//
// Credential IDs used below are only NAMES. The secret values live in Jenkins'
// credential store and are injected only inside withCredentials { } blocks.
// =============================================================================

pipeline {
    // Runs on any available agent. With several agents, pin one with a label:
    //   agent { label 'windows && android' }
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_LINT', defaultValue: true,
            description: 'Run Android Lint. Lint problems mark the build UNSTABLE instead of failing it.')
        booleanParam(name: 'RUN_CREDENTIALS_DEMO', defaultValue: false,
            description: 'Needs credentials: sample-api-token (Secret text), sample-config-file (Secret file).')
        booleanParam(name: 'BUILD_RELEASE', defaultValue: false,
            description: 'Needs credentials: android-release-keystore, android-keystore-password, android-release-key.')
    }

    environment {
        // NON-SECRET values only. Anything here is visible in the job's environment.
        // --no-daemon: no Gradle daemon left running between CI builds.
        // --console=plain: readable logs without progress-bar escape codes.
        GRADLE_ARGS = '--no-daemon --stacktrace --console=plain'

        // Optional: the default server URL compiled into the APK's BuildConfig.
        // app/build.gradle.kts reads env JENKINS_BASE_URL before local.properties.
        // JENKINS_BASE_URL = 'http://10.0.2.2:8080/'
    }

    stages {
        stage('Checkout') {
            steps {
                // Uses the repository + credentials from the job's "Pipeline script from SCM" settings.
                checkout scm
                bat 'git log -1 --oneline'
            }
        }

        stage('Verify environment') {
            steps {
                // Fails early with a clear message if Java or the Android SDK is missing.
                bat 'call ci\\windows\\verify-env.bat'
            }
        }

        stage('Gradle wrapper') {
            steps {
                // Downloads Gradle 9.5.0 on first use (into the Jenkins user's GRADLE_USER_HOME).
                bat 'gradlew.bat %GRADLE_ARGS% --version'
            }
        }

        stage('Compile') {
            steps {
                bat 'gradlew.bat %GRADLE_ARGS% :app:compileDebugSources'
            }
        }

        stage('Unit tests') {
            steps {
                // Writes JUnit XML to app/build/test-results/testDebugUnitTest/ (published in post).
                bat 'gradlew.bat %GRADLE_ARGS% :app:testDebugUnitTest'
            }
        }

        stage('Lint') {
            when { expression { params.RUN_LINT != false } }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                    bat 'gradlew.bat %GRADLE_ARGS% :app:lintDebug'
                }
            }
        }

        stage('Build debug APK') {
            steps {
                bat 'gradlew.bat %GRADLE_ARGS% :app:assembleDebug'
            }
        }

        stage('Credentials demo') {
            when { expression { params.RUN_CREDENTIALS_DEMO == true } }
            steps {
                // Secret text -> environment variable, ONLY inside this block.
                // Single-quoted Groovy string: Groovy does NOT interpolate it, so the
                // secret never becomes part of the command text. Gradle reads it
                // from the environment with providers.environmentVariable("SAMPLE_TOKEN").
                withCredentials([string(credentialsId: 'sample-api-token', variable: 'SAMPLE_TOKEN')]) {
                    bat 'gradlew.bat %GRADLE_ARGS% :app:verifySampleToken'
                }

                // Secret file -> Jenkins writes it to a temporary file and puts the PATH
                // in the variable. The file is deleted when the block ends.
                withCredentials([file(credentialsId: 'sample-config-file', variable: 'SAMPLE_CONFIG_FILE')]) {
                    bat '''
                        @echo off
                        if exist "%SAMPLE_CONFIG_FILE%" (
                            echo Secret file is available to this block. Its content is not printed.
                        ) else (
                            echo Secret file binding failed.
                            exit /b 1
                        )
                    '''
                }
            }
        }

        stage('Release APK (optional)') {
            when { expression { params.BUILD_RELEASE == true } }
            steps {
                // app/build.gradle.kts only configures release signing when all four
                // variables are present, so the APK is signed here and nowhere else.
                withCredentials([
                    file(credentialsId: 'android-release-keystore', variable: 'ANDROID_KEYSTORE_FILE'),
                    string(credentialsId: 'android-keystore-password', variable: 'ANDROID_KEYSTORE_PASSWORD'),
                    usernamePassword(credentialsId: 'android-release-key',
                                     usernameVariable: 'ANDROID_KEY_ALIAS',
                                     passwordVariable: 'ANDROID_KEY_PASSWORD'),
                ]) {
                    bat 'gradlew.bat %GRADLE_ARGS% :app:assembleRelease'
                }
            }
        }
    }

    post {
        always {
            // Test results appear on the build page ("Test Result") and as a trend chart.
            junit testResults: 'app/build/test-results/testDebugUnitTest/*.xml', allowEmptyResults: true

            // APKs appear under "Build Artifacts". fingerprint records checksums for traceability.
            archiveArtifacts artifacts: 'app/build/outputs/apk/**/*.apk', allowEmptyArchive: true, fingerprint: true

            // HTML test and lint reports, for reading failures in a browser.
            archiveArtifacts artifacts: 'app/build/reports/**', allowEmptyArchive: true
        }
        success {
            echo "Build #${env.BUILD_NUMBER} succeeded. Download app-debug.apk from Build Artifacts."
        }
        unstable {
            echo 'Build is UNSTABLE: lint reported problems. See app/build/reports/lint-results-debug.html in Build Artifacts.'
        }
        failure {
            echo 'Build FAILED. Read the first error in the console log; docs/06-troubleshooting.md lists common causes.'
        }
    }
}
