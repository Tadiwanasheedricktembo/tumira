package com.querycubix.queryshare.data.repository

import android.util.Log
import com.querycubix.queryshare.data.model.FileItem
import com.querycubix.queryshare.data.remote.ApiService
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class FileRepository {
    private var apiService: ApiService? = null
    private var currentBaseUrl: String? = null

    fun updateBaseUrl(ip: String, port: Int) {
        val baseUrl = "http://$ip:$port/"
        if (currentBaseUrl == baseUrl) return

        val logging = HttpLoggingInterceptor { message ->
            Log.d("FileRepository", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS) // Shorter timeout for discovery checks
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        apiService = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        
        currentBaseUrl = baseUrl
    }

    suspend fun ping(): Response<ResponseBody>? {
        return try {
            val response = apiService?.ping()
            Log.d("FileRepository", "Ping result for $currentBaseUrl: status=${response?.code()}, body=${response?.body()?.string() ?: "empty"}")
            response
        } catch (e: Exception) {
            Log.e("FileRepository", "Ping failed for $currentBaseUrl", e)
            null
        }
    }

    suspend fun getFiles(): Result<List<FileItem>> {
        return try {
            val service = apiService ?: return Result.failure(Exception("Not connected to server"))
            val files = service.listFiles()
            Log.d("FileRepository", "GetFiles result for $currentBaseUrl: count=${files.size}")
            Result.success(files)
        } catch (e: Exception) {
            Log.e("FileRepository", "GetFiles failed for $currentBaseUrl", e)
            Result.failure(e)
        }
    }

    suspend fun uploadFile(multipartBody: MultipartBody.Part): Result<Unit> {
        return try {
            val service = apiService ?: return Result.failure(Exception("Not connected to server"))
            val response = service.uploadFile(multipartBody)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Upload failed: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(fileId: String) = try {
        val service = apiService ?: throw Exception("Not connected to server")
        service.downloadFile(fileId)
    } catch (e: Exception) {
        null
    }
    
    suspend fun checkConnection(): Boolean {
        return ping()?.isSuccessful == true
    }
}
