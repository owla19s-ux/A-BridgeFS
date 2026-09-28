package com.abridgefs.app

object BridgeRequest {
    private val block = Regex("""(?s)\[bridgefs\](.*?)\[/bridgefs\]""", RegexOption.IGNORE_CASE)
    fun extract(text: String): String? = block.find(text)?.groupValues?.get(1)?.trim()
}
