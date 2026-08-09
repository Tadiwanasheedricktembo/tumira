package com.querycubix.queryshare.service

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.querycubix.queryshare.data.repository.FileRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = FileRepository()

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString("file_uri") ?: return Result.failure()
        val serverIp = inputData.getString("server_ip") ?: return Result.failure()
        val serverPort = inputData.getInt("server_port", 8080)
        
        val fileUri = Uri.parse(fileUriString)
        repository.updateBaseUrl(serverIp, serverPort)

        return try {
            val file = getFileFromUri(fileUri) ?: return Result.failure()
            val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            // In a real app, you'd want to track progress here.
            // Simplified progress reporting:
            setProgress(workDataOf("progress" to 50))
            
            val response = repository.uploadFile(body)

            if (response.isSuccess) {
                setProgress(workDataOf("progress" to 100))
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver = applicationContext.contentResolver
        val fileName = getFileName(uri) ?: "upload_file"
        val tempFile = File(applicationContext.cacheDir, fileName)
        
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = applicationContext.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }
}
