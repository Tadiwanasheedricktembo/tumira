package com.querycubix.queryshare.data.model

data class FileItem(
    val id: String,
    val filename: String,
    val filesize: Long,
    val mimeType: String? = null,
    val uploadDate: String? = null,
    val url: String? = null
) {
    val name: String get() = filename
    val size: Long get() = filesize
    val path: String get() = url ?: filename
    val type: FileType get() = mimeType?.let { FileType.fromMimeType(it) } ?: FileType.OTHER
}

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, APK, ARCHIVE, OTHER;

    companion object {
        fun fromMimeType(mimeType: String): FileType = when {
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType.startsWith("audio/") -> AUDIO
            mimeType == "application/pdf" || mimeType.startsWith("text/") -> DOCUMENT
            mimeType == "application/vnd.android.package-archive" -> APK
            mimeType == "application/zip" || mimeType == "application/x-zip-compressed" -> ARCHIVE
            else -> OTHER
        }
    }
}
