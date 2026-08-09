package com.querycubix.queryshare.service

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querycubix.queryshare.data.repository.FileRepository
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = FileRepository()

    override suspend fun doWork(): Result {
        val fileId = inputData.getString("file_id") ?: return Result.failure()
        val serverIp = inputData.getString("server_ip") ?: return Result.failure()
        val serverPort = inputData.getInt("server_port", 8080)

        repository.updateBaseUrl(serverIp, serverPort)

        return try {
            val response = repository.downloadFile(fileId)

            if (response != null && response.isSuccessful) {
                val body = response.body() ?: return Result.failure()
                val fileName = resolveFileName(response.headers()?.get("Content-Disposition"))
                val downloadsDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: applicationContext.filesDir
                val file = File(downloadsDir, fileName)

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun resolveFileName(contentDisposition: String?): String {
        val suggestedName = contentDisposition
            ?.let { Regex("filename=\\\"?([^\\\";]+)\\\"?").find(it) }
            ?.groups?.get(1)?.value
        return suggestedName ?: "downloaded_file"
    }
}
