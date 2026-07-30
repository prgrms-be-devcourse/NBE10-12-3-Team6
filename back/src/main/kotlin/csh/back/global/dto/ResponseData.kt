package csh.back.global.dto

data class ResponseData<T>(
    val statusCode: Int,
    val data: T
)