package com.abridgefs.app

object BridgeRequest {
    private val block = Regex("""(?s)\[bridgefs\](.*?)\[/bridgefs\]""", RegexOption.IGNORE_CASE)

    fun extractAll(text: String): List<String> =
        block.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()

    fun extract(text: String): String? = extractAll(text).firstOrNull()
}
