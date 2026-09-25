package com.example.data.remote

import com.example.data.model.ActivityLogEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class SyncRequest(
    val child_id: String,
    val logs: List<ActivityLogEntity>,
    val locations: List<LocationPointEntity>
)

data class SyncResponse(
    val status: String,
    val synced_logs: Int,
    val flagged_threats: Int,
    val synced_locations: Int
)

data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: Long
)

interface FocusSenseApiService {

    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("/api/sync")
    suspend fun syncData(@Body payload: SyncRequest): Response<SyncResponse>

    @GET("/api/logs/{child_id}")
    suspend fun getLogs(
        @Path("child_id") childId: String,
        @Query("flagged_only") flaggedOnly: Boolean = false
    ): Response<List<ActivityLogEntity>>

    @DELETE("/api/logs/{log_id}")
    suspend fun deleteLog(@Path("log_id") logId: String): Response<Map<String, String>>

    @GET("/api/schedules/{child_id}")
    suspend fun getSchedules(@Path("child_id") childId: String): Response<List<ScheduleRuleEntity>>

    @POST("/api/schedules")
    suspend fun saveSchedule(@Body rule: ScheduleRuleEntity): Response<Map<String, String>>

    @DELETE("/api/schedules/{rule_id}")
    suspend fun deleteSchedule(@Path("rule_id") ruleId: String): Response<Map<String, String>>

    @POST("/api/location/report")
    suspend fun reportLocation(@Body point: LocationPointEntity): Response<Map<String, String>>

    @GET("/api/location/{child_id}/latest")
    suspend fun getLatestLocation(@Path("child_id") childId: String): Response<LocationPointEntity>
}

object ApiClient {
    private var currentBaseUrl: String = "https://focussense-api.example.com" // Or http://10.0.2.2:8000 for local

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getService(baseUrl: String = currentBaseUrl): FocusSenseApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(FocusSenseApiService::class.java)
    }
}
