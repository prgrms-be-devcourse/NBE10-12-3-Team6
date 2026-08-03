package csh.back.domain.trip.post.reminder.service

import csh.back.domain.trip.post.repository.PostRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PostReminderService(
    private val postRepository: PostRepository,
    private val postReminderSender: PostReminderSender
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun sendOneYearAgoPostReminders(
        today: LocalDate = LocalDate.now()
    ) {
        val targetDate = today.minusYears(1)
        val startAt = targetDate.atStartOfDay()
        val endAt = targetDate
            .plusDays(1)
            .atStartOfDay()

        val postIds =
            postRepository
                .findIdsByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    startAt = startAt,
                    endAt = endAt
                )

        postIds.forEach { postId ->
            runCatching {
                postReminderSender.send(postId)
            }.onFailure { exception ->
                log.error(
                    "포스트 리마인드 처리 실패. postId={}",
                    postId,
                    exception
                )
            }
        }
    }
}