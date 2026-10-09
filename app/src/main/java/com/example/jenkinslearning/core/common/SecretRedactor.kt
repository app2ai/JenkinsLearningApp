package com.example.jenkinslearning.core.common

/**
 * Masks secret-looking values in text before it reaches the screen.
 *
 * Jenkins already masks values bound with withCredentials, but only the exact
 * strings it knows about, and only in its own console. Encoded or transformed
 * secrets, secrets printed by tools, and values set outside withCredentials
 * aren't masked. This class is a second, best-effort layer. It can't
 * guarantee every secret is caught, so don't print secrets in the first place.
 */
class SecretRedactor(
    /** Exact values to always mask, e.g. the user's own API token for this session. */
    private val knownSecrets: () -> List<String> = { emptyList() },
) {

    data class Result(val text: String, val redactions: Int)

    fun redact(input: String): Result {
        var count = 0
        var text = input

        // 1. Exact known secrets (only if long enough to avoid masking ordinary words).
        knownSecrets().filter { it.length >= MIN_KNOWN_SECRET_LENGTH }.distinct().forEach { secret ->
            val occurrences = text.windowedCount(secret)
            if (occurrences > 0) {
                count += occurrences
                text = text.replace(secret, MASK)
            }
        }

        // 2. Pattern-based masking.
        for ((regex, replacement) in RULES) {
            text = regex.replace(text) { match ->
                count++
                replacement(match)
            }
        }
        return Result(text, count)
    }

    private fun String.windowedCount(needle: String): Int {
        var index = indexOf(needle)
        var n = 0
        while (index >= 0) {
            n++
            index = indexOf(needle, index + needle.length)
        }
        return n
    }

    companion object {
        const val MASK = "****"
        private const val MIN_KNOWN_SECRET_LENGTH = 6

        private val RULES: List<Pair<Regex, (MatchResult) -> String>> = listOf(
            // Authorization: Basic xxx / Bearer xxx
            Regex("""(?i)(authorization\s*[:=]\s*(?:basic|bearer|token)\s+)[A-Za-z0-9+/=._~-]+""") to
                { m -> m.groupValues[1] + MASK },
            // https://user:secret@host
            Regex("""(?i)(https?://)[^\s/:@]+:[^\s/@]+@""") to
                { m -> m.groupValues[1] + "$MASK:$MASK@" },
            // password=..., token: ..., api_key="..." (keeps the key name, masks the value)
            Regex("""(?i)\b((?:[a-z0-9_.-]*?)(?:password|passwd|pwd|secret|token|api[_-]?key|apikey|access[_-]?key)[a-z0-9_.-]*)(\s*[:=]\s*)(["']?)(?!\*{4})[^\s"'&,;]{3,}\3""") to
                { m -> m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + MASK + m.groupValues[3] },
            // GitHub tokens
            Regex("""\bgh[pousr]_[A-Za-z0-9]{30,}\b""") to { MASK },
            Regex("""\bgithub_pat_[A-Za-z0-9_]{30,}\b""") to { MASK },
            // AWS access key ids
            Regex("""\bAKIA[0-9A-Z]{16}\b""") to { MASK },
            // JWTs
            Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""") to { MASK },
            // PEM private keys
            Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----""") to
                { "-----BEGIN PRIVATE KEY-----$MASK-----END PRIVATE KEY-----" },
        )
    }
}
