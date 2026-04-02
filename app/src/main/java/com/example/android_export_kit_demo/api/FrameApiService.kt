package com.example.android_export_kit_demo.api

import com.example.android_export_kit_demo.model.CategoryResponse
import com.example.android_export_kit_demo.model.FrameByCategoryResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface FrameApiService {
    @GET("api/get_dynamic_photo_frame_category")
    suspend fun getCategories(
        @Header("Authorization") token: String
    ): Response<CategoryResponse>

    @POST("api/get_dynamic_photo_frame_by_category_id")
    suspend fun getFramesByCategory(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<FrameByCategoryResponse>
}
