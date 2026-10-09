package com.example.jenkinslearning.domain.usecase

import com.example.jenkinslearning.domain.model.ConfigConcept
import com.example.jenkinslearning.domain.model.CredentialGuideEntry
import com.example.jenkinslearning.domain.model.LearningTopic
import com.example.jenkinslearning.domain.repository.LearningContentRepository

data class CredentialGuide(
    val credentials: List<CredentialGuideEntry>,
    val concepts: List<ConfigConcept>,
)

class GetCredentialGuideUseCase(private val repository: LearningContentRepository) {
    operator fun invoke(): CredentialGuide =
        CredentialGuide(repository.credentialGuide(), repository.configConcepts())
}

class GetLearningTopicsUseCase(private val repository: LearningContentRepository) {
    operator fun invoke(): List<LearningTopic> = repository.learningTopics()
}
