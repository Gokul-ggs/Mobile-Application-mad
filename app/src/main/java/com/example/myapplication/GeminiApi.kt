package com.example.myapplication

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
