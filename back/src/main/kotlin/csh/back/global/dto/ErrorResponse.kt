package csh.back.global.dto

data class ErrorResponse(
    val statusCode: Int,
    val message: String?
)