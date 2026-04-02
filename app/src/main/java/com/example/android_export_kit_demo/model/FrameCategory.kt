package com.example.android_export_kit_demo.model

import com.google.gson.annotations.SerializedName

data class CategoryResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: List<FrameCategory>
)

data class FrameCategory(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("thumbnail") val thumbnail: String
)

data class FrameByCategoryResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: CategoryData
)

data class CategoryData(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("frames") val frames: List<ApiFrame>
)

data class ApiFrame(
    @SerializedName("id") val id: Int,
    @SerializedName("zip_file") val zipFile: String,
    @SerializedName("input_count") val inputCount: Int,
    @SerializedName("thumbnail") val thumbnail: String
)
