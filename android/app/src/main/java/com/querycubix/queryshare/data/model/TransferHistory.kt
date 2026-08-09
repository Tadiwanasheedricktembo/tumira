package com.querycubix.queryshare.data.model

data class TransferHistory(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val fileSize: Long,
    val date: Long = System.currentTimeMillis(),
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Int = 0,
    val speed: String = ""
)

enum class TransferDirection {
    UPLOAD, DOWNLOAD
}

enum class TransferStatus {
    PENDING, ONGOING, COMPLETED, FAILED, CANCELLED
}
