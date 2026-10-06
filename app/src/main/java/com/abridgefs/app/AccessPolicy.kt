package com.abridgefs.app

import android.content.Context

object AccessPolicy {
    private const val PREFS = "bridgefs"
    private const val API_ENABLED = "api_access_enabled"
    private const val GITHUB_ENABLED = "github_access_enabled"
    private const val STANDALONE_CONSTRUCTION_ENABLED = "standalone_construction_enabled"

    fun isApiEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(API_ENABLED, true)

    fun isGithubEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(GITHUB_ENABLED, false)

    fun isStandaloneConstructionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(STANDALONE_CONSTRUCTION_ENABLED, false)

    fun setApiEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(API_ENABLED, enabled).apply()
    }

    fun setGithubEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(GITHUB_ENABLED, enabled).apply()
    }

    fun setStandaloneConstructionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(STANDALONE_CONSTRUCTION_ENABLED, enabled).apply()
    }
}
