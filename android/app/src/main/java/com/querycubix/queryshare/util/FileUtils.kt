package com.querycubix.queryshare.util

fun formatFileSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var s = size.toDouble()
    var unitIndex = 0
    while (s >= 1024 && unitIndex < units.size - 1) {
        s /= 1024
        unitIndex++
    }
    return "%.2f %s".format(s, units[unitIndex])
}
