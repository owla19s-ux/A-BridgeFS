package com.abridgefs.app

import org.json.JSONObject

/**
 * Minimal local Tool Calling runtime.
 *
 * Phase 2 deliberately exposes one deterministic tool only:
 * echo_test. The runtime owns tool execution and feeds the result back
 * to the model until the model returns a normal assistant message.
 */
object BridgeToolRuntime {
    const val MAX_TOOL_ROUNDS = 4

    fun definitions(): org.json.JSONArray = org.json.JSONArray().put(
        JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", "echo_test")
                .put("description", "Return the supplied text unchanged. Used only to verify Tool Calling.")
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("text", JSONObject()
                            .put("type", "string")
                            .put("description", "Text to echo back.")))
                    .put("required", org.json.JSONArray().put("text")))
            )
    )

    fun execute(name: String, arguments: String): String {
        return when (name) {
            "echo_test" -> {
                val text = runCatching {
                    JSONObject(arguments).optString("text")
                }.getOrDefault("")
                JSONObject()
                    .put("tool", "echo_test")
                    .put("echo", text)
                    .put("success", true)
                    .toString()
            }
            else -> JSONObject()
                .put("tool", name)
                .put("success", false)
                .put("error", "Unknown tool")
                .toString()
        }
    }
}
