package csh.back.domain.trip.post.event

import csh.back.domain.trip.post.service.S3UploadService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class PostDeletedEventListenerTest {

    private val s3UploadService = mock(S3UploadService::class.java)
    private val listener = PostDeletedEventListener(s3UploadService)

    @Test
    fun `삭제 이벤트의 모든 이미지 URL을 S3 삭제 서비스에 전달한다`() {
        val urls = listOf(
            "https://storage.example.com/original.jpg",
            "https://storage.example.com/normal.webp",
            "https://storage.example.com/data-saver.webp"
        )

        listener.handle(PostDeletedEvent(urls))

        verify(s3UploadService).deleteImages(*urls.toTypedArray())
    }
}
