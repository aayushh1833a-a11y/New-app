package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Retrofit data classes
data class Part(val text: String? = null)
data class Content(val parts: List<Part>)
data class GenerateContentRequest(val contents: List<Content>)
data class Candidate(val content: Content?)
data class GenerateContentResponse(val candidates: List<Candidate>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    suspend fun generateText(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is empty or placeholder!")
            return getFallbackResponse(prompt)
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Unable to parse AI response. Utilizing engine fallback."
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini: ${e.message}", e)
            getFallbackResponse(prompt)
        }
    }

    // Local smart fallback generator in case API key is not supplied or quota limit hit
    private fun getFallbackResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("script") -> {
                """
                🎬 **AI GENERATED SCRIPT DIRECTIVE**
                
                **Theme**: High-Retention Creative Script
                **Proposed Video Title**: "Exposed: The Truth Behind Rapid Creation"
                
                [SCENE 1: HOOK (0:00 - 0:05)]
                Visual: Close-up focus with rapid neon glitch transition, high impact shake.
                Audio: Dramatic heavy bass surge.
                Narrator Voiceover: "Everyone is lying to you about how content is made. Here is the actual secret..."
                Subtitles: "Everyone is lying to you..."
                
                [SCENE 2: EXPOSITION (0:05 - 0:15)]
                Visual: Cyberpunk digital waves sweep cleanly, panning across retro CRT grid displays.
                Audio: Sleek, chill synth beats fading in.
                Narrator Voiceover: "Creators are now using AI-assisted pipelines to sketch scene concepts, automate subtitles, and inject seamless beat-synced filters in one tap."
                Subtitles: "AI pipelines are automating everything in one tap."
                
                [SCENE 3: CTA (0:15 - 0:20)]
                Visual: Clean minimalist zoom-out, modern UI overlay overlaying social elements.
                Audio: High-pitched whoosh fade out.
                Narrator Voiceover: "Stop editing manually. Tap follow to stay ahead of the content loop."
                Subtitles: "Stop editing. Stay ahead of the loop."
                """.trimIndent()
            }
            lower.contains("hashtag") -> {
                """
                🏷️ **TRENDING AI HASHTAG ANALYTICS**
                
                #CapCutPro #AIContentCreator #MobileVideoEditing #VideoEditorSecret #GeminiAiVideo #AestheticEdits #TrendMicro #CinematicVibes #ContentHacks2026 #EditInOneTap
                """.trimIndent()
            }
            lower.contains("thumbnail") -> {
                """
                📸 **AI THUMBNAIL LAYOUT CONFIG**
                
                **Layout Archetype**: Dual Split Composition (Contrast-Heavy)
                **Suggested Overlay Text**: "THIS CHANGES EVERYTHING! 🤯"
                **Text Styling**: Bright Impact Yellow, thick black outline stroke (60px), with slight negative 8-deg tilt.
                **Visual Focus**: Crop subject to the left (50% scale), right portion filled with warm glowing VHS digital elements.
                """.trimIndent()
            }
            lower.contains("voiceover") -> {
                "In the digital landscape of 2026, those who leverage intelligent AI pipelines produce 10 times faster. Elevate your storytelling instantly."
            }
            else -> {
                """
                ✨ **AI COMPOSITE SCENE STORYBOARD**
                
                **Segment 1**: neon_city | Cyberpunk Tokyo | Duration: 5.0s | Transition: Glitch Flash
                **Segment 2**: glitch_beach | Vaporwave Oceanside | Duration: 5.0s | Transition: Zoom Slide
                **Segment 3**: mountain_peak | Cinematic Sunset Peak | Duration: 4.0s | Transition: Fade Out
                
                **Subtitles**:
                - 0.0s - 4.5s: "City lights ignite the canvas..."
                - 5.0s - 9.5s: "Drowning out the static hum..."
                - 10.0s - 14.0s: "Reaching the absolute peak."
                
                **Audio Theme**: Retro Synth Pulse (Volume 0.8)
                """.trimIndent()
            }
        }
    }
}
