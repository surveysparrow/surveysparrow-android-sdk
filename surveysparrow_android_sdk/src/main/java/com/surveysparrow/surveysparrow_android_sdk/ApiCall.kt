package com.surveysparrow.surveysparrow_android_sdk

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    fun create(baseUrl: String): ApiService {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.SECONDS)
            .readTimeout(3000, TimeUnit.SECONDS)
            .writeTimeout(3000, TimeUnit.SECONDS)
            .addInterceptor(ErrorInterceptor())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}

interface ApiService {
    @POST("/api/internal/spotcheck/widget/{targetToken}/properties")
    suspend fun fetchProperties(
        @Path("targetToken") targetToken: String,
        @Body payload: PropertiesRequestPayload
    ): PropertiesApiResponse

    @POST("/api/internal/spotcheck/widget/{targetToken}/eventTrigger")
    suspend fun sendEventTrigger(
        @Path("targetToken") targetToken: String,
        @Body payload: EventRequestPayload
    ): EventApiResponse

    @PUT("/api/internal/spotcheck/dismiss/{spotCheckContactID}")
    suspend fun closeSpotCheck(
        @Path("spotCheckContactID") spotCheckContactID: String,
        @Body payload: DismissPayload
    ): CloseSpotCheckResponce
}

class ErrorInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            Log.d("SPOT-CHECK-${response.code().toString()}", response.message())
        }

        return response
    }
}

data class EventRequestPayload(
    val screenName: String?,
    val variables: Map<String, Any>,
    val customProperties: Map<String, Any>,
    val userDetails: HashMap<String, String>,
    val visitor: Visitor,
    val spotCheckId: Int,
    val eventTrigger: Map<String, Map<String, Any>>,
    val traceId: String
)

data class EventApiResponse(
    val eventShow: Boolean?,
    val survey_id: String?,
    val spotCheckId: String?,
    val spotCheckContactId: String?,
    val appearance: Map<String, Any>?,
    val checkCondition: Map<String, Any>?,
    val show: Boolean?,
    val triggerToken: String
)

data class PropertiesRequestPayload(
    val screenName: String?,
    val variables: Map<String, Any>,
    val userDetails: HashMap<String, String>,
    val visitor: Visitor,
    val customProperties: Map<String, Any>,
    val traceId: String
)

data class PropertiesApiResponse(
    val show: Boolean?,
    val survey_id: String?,
    val spotCheckId: String?,
    val spotCheckContactId: String?,
    val appearance: Map<String, Any>?,
    val checkPassed: Boolean?,
    val checkCondition: Map<String, Any>?,
    val multiShow: Boolean?,
    val resultantSpotCheck: List<Map<String, Any>?>?,
    val triggerToken: String,
    val uuid: String?
)

fun PropertiesApiResponse.toMap(): Map<String, Any?> {
    return mapOf(
        "show" to this.show,
        "survey_id" to this.survey_id,
        "spotCheckId" to this.spotCheckId,
        "spotCheckContactId" to this.spotCheckContactId,
        "appearance" to this.appearance,
        "checkPassed" to this.checkPassed,
        "checkCondition" to this.checkCondition,
        "multiShow" to this.multiShow,
        "resultantSpotCheck" to this.resultantSpotCheck
    )
}

data class Visitor(
    val deviceType: String,
    val operatingSystem: String,
    val screenResolution: ScreenResolution,
    val currentDate: String,
    val timezone: String
)

data class ScreenResolution(
    val width: Int,
    val height: Int
)

data class SpotCheckData(
    val type: String,
    val data: SpotCheckDataDetails
)

data class SpotCheckDataDetails(
    val currentQuestionSize: CurrentQuestionSize
)

data class CurrentQuestionSize(
    val height: Double
)

data class DismissPayload (
    val traceId: String,
    val triggerToken: String
)

data class CloseSpotCheckResponce(
    val success: Boolean?,
    val message: String?
)
