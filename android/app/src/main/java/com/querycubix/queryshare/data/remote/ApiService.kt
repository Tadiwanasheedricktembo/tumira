package com.querycubix.queryshare.data.remote

import com.querycubix.queryshare.data.model.FileItem
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("api/files")
    suspend fun listFiles(): List<FileItem>

    @GET("api/ping")
    suspend fun ping(): Response<ResponseBody>

    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Streaming
    @GET("api/files/{id}")
    suspend fun downloadFile(
        @Path("id") fileId: String
    ): Response<ResponseBody>

    @GET("api/ping")
    suspend fun checkStatus(): Response<Unit>
}
