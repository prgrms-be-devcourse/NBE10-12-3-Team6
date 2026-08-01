package csh.back.domain.trip.post.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.PutObjectRequest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import java.net.URI

class S3UploadServiceTest {
    private val amazonS3 = mock(AmazonS3::class.java)
    private val service = S3UploadService(amazonS3, BUCKET)

    @Test
    @DisplayName("원본과 일반용 및 데이터 절약용 이미지를 서로 다른 경로에 저장한다")
    fun uploadAllImageVariants() {
        `when`(amazonS3.getUrl(eq(BUCKET), anyString())).thenAnswer { invocation ->
            URI("https://storage.example.com/$BUCKET/${invocation.arguments[1]}").toURL()
        }
        val file = MockMultipartFile(
            "image",
            "photo.jpg",
            "image/jpeg",
            "original".toByteArray()
        )
        val variants = PostImageProcessor.PostImageVariants(
            normal = encodedImage("normal"),
            dataSaver = encodedImage("data-saver")
        )

        val result = service.uploadImages(file, variants)

        val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        verify(amazonS3, times(3)).putObject(requestCaptor.capture())
        val objectKeys = requestCaptor.allValues.map { it.key }
        assertTrue(objectKeys.any { it.startsWith("posts/original/") })
        assertTrue(objectKeys.any { it.startsWith("posts/normal/") })
        assertTrue(objectKeys.any { it.startsWith("posts/data-saver/") })
        assertTrue(result.originalUrl.contains("/posts/original/"))
        assertTrue(result.normalUrl.contains("/posts/normal/"))
        assertTrue(result.dataSaverUrl.contains("/posts/data-saver/"))
    }

    @Test
    @DisplayName("게시글 삭제 시 원본과 두 변환 이미지의 객체를 모두 삭제한다")
    fun deleteAllImageVariants() {
        service.deleteImages(
            "https://storage.example.com/$BUCKET/posts/original/original.jpg",
            "https://storage.example.com/$BUCKET/posts/normal/normal.webp",
            "https://storage.example.com/$BUCKET/posts/data-saver/data-saver.webp"
        )

        verify(amazonS3).deleteObject(BUCKET, "posts/original/original.jpg")
        verify(amazonS3).deleteObject(BUCKET, "posts/normal/normal.webp")
        verify(amazonS3).deleteObject(BUCKET, "posts/data-saver/data-saver.webp")
    }

    private fun encodedImage(value: String) = PostImageProcessor.EncodedImage(
        bytes = value.toByteArray(),
        contentType = "image/webp",
        extension = "webp"
    )

    companion object {
        private const val BUCKET = "triplog-bucket"
    }
}
