package com.xiaozhi.smarthome

import retrofit2.Response
import retrofit2.http.*

interface HomeAssistantApi {
    @GET("api/states")
    suspend fun getAllStates(): Response<List<HaEntity>>

    @GET("api/states/{entityId}")
    suspend fun getEntityState(@Path("entityId") entityId: String): Response<HaEntity>

    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body body: Map<String, Any>
    ): Response<Unit>

    @GET("api/config")
    suspend fun getConfig(): Response<Map<String, Any>>
}