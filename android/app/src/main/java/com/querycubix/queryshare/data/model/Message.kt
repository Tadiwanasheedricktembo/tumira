package com.querycubix.queryshare.data.model

data class Message(
    val id: String,
    val from: String,
    val fromName: String,
    val to: String?,
    val text: String,
    val timestamp: String,
    val isLocal: Boolean = false
)
