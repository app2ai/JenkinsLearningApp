package com.example.jenkinslearning.domain.model

enum class CredentialKind(val displayName: String) {
    USERNAME_PASSWORD("Username with password"),
    SECRET_TEXT("Secret text"),
    SECRET_FILE("Secret file"),
    SSH_KEY("SSH username with private key"),
}

enum class CredentialScope(val displayName: String) {
    GLOBAL("Global (Jenkins)"),
    FOLDER("Folder"),
    SYSTEM("System"),
}

/**
 * Illustrative METADATA about a Jenkins credential, used by this project's
 * Jenkinsfile. There's no secret value field on purpose: the app never reads
 * secrets from Jenkins and can't display them.
 */
data class CredentialGuideEntry(
    val id: String,
    val kind: CredentialKind,
    val scope: CredentialScope,
    val purpose: String,
    val configuredAt: String,
    val consumedBy: String,
    val pipelineSnippet: String,
)

/** One row of the "where does this value live?" comparison. */
data class ConfigConcept(
    val title: String,
    val whereItLives: String,
    val whoReadsIt: String,
    val safeForSecrets: String,
    val example: String,
)

data class LearningTopic(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val codeSample: String?,
)
