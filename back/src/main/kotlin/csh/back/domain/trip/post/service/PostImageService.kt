package csh.back.domain.trip.post.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("dev")
@Service
class PostImageService(
    @Value("\${file.upload.dir}")
    private val uploadDir: String,
    @Value("\${file.upload.base-url}")
    private val baseUrl: String
)
