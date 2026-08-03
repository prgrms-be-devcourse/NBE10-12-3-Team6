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
import java.text.Normalizer
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

        val originalFilename = sanitizeOriginalFilename(file.originalFilename)
        val extension = originalFilename
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
                originalFilename = originalFilename,
                originalUrl = originalUrl,
                normalUrl = uploadedNormalUrl,
                dataSaverUrl = dataSaverUrl
            )
        } catch (exception: RuntimeException) {
            deleteImages(originalUrl, normalUrl)
            throw exception
        }
    }

    private fun sanitizeOriginalFilename(filename: String?): String? {
        val basename = filename
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?: return null

        return Normalizer.normalize(basename, Normalizer.Form.NFC)
            .replace(CONTROL_CHARACTERS, "")
            .trim()
            .take(MAX_ORIGINAL_FILENAME_LENGTH)
            .takeIf { it.isNotBlank() }
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
        val path = URI(imageUrl).path.removePrefix("/")
        val objectKey = path.removePrefix("$bucket/")
        require(objectKey.isNotBlank()) {
            "삭제할 S3 객체 키를 이미지 URL에서 찾을 수 없습니다."
        }
        amazonS3.deleteObject(bucket, objectKey)
    }

    data class UploadedPostImages(
        val originalFilename: String?,
        val originalUrl: String,
        val normalUrl: String,
        val dataSaverUrl: String
    )

    companion object {
        private const val MAX_ORIGINAL_FILENAME_LENGTH = 255
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]")
    }
}
