package csh.back.domain.trip.post.event

import csh.back.domain.trip.post.service.S3UploadService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PostDeletedEventListener(
    private val s3UploadService: S3UploadService
) {

    @Async("postDeleteExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PostDeletedEvent) {
        s3UploadService.deleteImages(*event.imageUrls.toTypedArray())
    }
}
