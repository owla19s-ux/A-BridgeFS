package com.abridgefs.app.github.api

import com.abridgefs.app.github.GitHubCredential

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GitHubApiFactory {
    fun create(token: String): GitHubApi {
        val auth = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build())
        }
        val client = OkHttpClient.Builder().addInterceptor(auth).build()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }
    fun create(credential: GitHubCredential): GitHubApi = create(credential.accessToken)
}
