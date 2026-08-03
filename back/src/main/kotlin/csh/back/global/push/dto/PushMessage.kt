package csh.back.global.push.dto

data class PushMessage(
    val title: String,
    val body: String,
    val targetUrl: String,
    val postId: Long
)