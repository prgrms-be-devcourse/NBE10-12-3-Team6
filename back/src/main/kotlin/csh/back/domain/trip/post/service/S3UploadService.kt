package csh.back.domain.trip.post.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.util.UUID

@Service
class S3UploadService(
    private val amazonS3: AmazonS3,
    @Value("\${app.s3.bucket}")
    private val bucket: String
) {
    @Throws(IOException::class)
    fun uploadImages(
        file: MultipartFile?,
        variants: PostImageProcessor.PostImageVariants
    ): UploadedPostImages {
        require(file != null && !file.isEmpty) { "업로드된 이미지가 없습니다." }

        val extension = file.originalFilename
            ?.takeIf { it.contains('.') }
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
            ?.let { ".$it" }
            .orEmpty()
        val originalUrl = file.inputStream.use { input ->
            upload(
                objectKey = "posts/original/${UUID.randomUUID()}$extension",
                input = input,
                contentLength = file.size,
                contentType = file.contentType
            )
        }

        var normalUrl: String? = null
        return try {
            val uploadedNormalUrl = upload(
                objectKey = "posts/normal/${UUID.randomUUID()}.${variants.normal.extension}",
                input = ByteArrayInputStream(variants.normal.bytes),
                contentLength = variants.normal.bytes.size.toLong(),
                contentType = variants.normal.contentType
            )
            normalUrl = uploadedNormalUrl
            val dataSaverUrl = upload(
                objectKey = "posts/data-saver/${UUID.randomUUID()}.${variants.dataSaver.extension}",
                input = ByteArrayInputStream(variants.dataSaver.bytes),
                contentLength = variants.dataSaver.bytes.size.toLong(),
                contentType = variants.dataSaver.contentType
            )
            UploadedPostImages(
                originalUrl = originalUrl,
                normalUrl = uploadedNormalUrl,
                dataSaverUrl = dataSaverUrl
            )
        } catch (exception: RuntimeException) {
            deleteImages(originalUrl, normalUrl)
            throw exception
        }
    }

    fun deleteImages(vararg imageUrls: String?) {
        imageUrls.filterNotNull().forEach(::deleteImage)
    }

    private fun upload(
        objectKey: String,
        input: InputStream,
        contentLength: Long,
        contentType: String?
    ): String {
        val metadata = ObjectMetadata().apply {
            this.contentType = contentType ?: "application/octet-stream"
            this.contentLength = contentLength
        }

        amazonS3.putObject(
            PutObjectRequest(
                bucket,
                objectKey,
                input,
                metadata
            )
                .withCannedAcl(CannedAccessControlList.PublicRead)
        )
        return amazonS3.getUrl(bucket, objectKey).toString()
    }

    private fun deleteImage(imageUrl: String) {
        runCatching {
            val path = URI(imageUrl).path.removePrefix("/")
            val objectKey = path.removePrefix("$bucket/")
            if (objectKey.isNotBlank()) {
                amazonS3.deleteObject(bucket, objectKey)
            }
        }
    }

    data class UploadedPostImages(
        val originalUrl: String,
        val normalUrl: String,
        val dataSaverUrl: String
    )
}
