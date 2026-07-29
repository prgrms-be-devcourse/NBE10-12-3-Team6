package csh.back.domain.trip.post.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.util.UUID

@Service
class S3UploadService(
    private val amazonS3: AmazonS3,
    @Value("\${app.s3.bucket}")
    private val bucket: String
) {
    @Throws(IOException::class)
    fun uploadImage(file: MultipartFile?): String {
        require(file != null && !file.isEmpty) { "업로드된 이미지가 없습니다." }

        val extension = file.originalFilename
            ?.takeIf { it.contains('.') }
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.let { ".$it" }
            .orEmpty()
        val fileName = "${UUID.randomUUID()}$extension"

        val metadata = ObjectMetadata().apply {
            contentType = file.contentType
            contentLength = file.size
        }

        amazonS3.putObject(
            PutObjectRequest(bucket, fileName, file.inputStream, metadata)
                .withCannedAcl(CannedAccessControlList.PublicRead)
        )
        return amazonS3.getUrl(bucket, fileName).toString()
    }
}
