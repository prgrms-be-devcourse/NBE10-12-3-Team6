package csh.back.domain.trip.post.reminder.service

import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.global.notification.entity.NotificationType
import csh.back.global.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class PostReminderService(
    private val postRepository: PostRepository,
    private val notificationService: NotificationService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createOneYearReminders(): Int {
        val reminderCutoff =
            LocalDateTime.now(SEOUL_ZONE)
                .minusYears(1)

        val reminderTargets =
            postRepository.findOneYearReminderTargets(
                reminderCutoff
            )

        var createdCount = 0

        reminderTargets.forEach { post ->
            try {
                val created =
                    createReminderForPost(post)

                if (created) {
                    createdCount++
                }
            } catch (e: DataIntegrityViolationException) {
                log.debug(
                    "이미 생성된 POST 리마인드 알림입니다. postId={}",
                    post.id
                )
            } catch (e: RuntimeException) {
                log.error(
                    "POST 리마인드 알림 생성 중 오류가 발생했습니다. postId={}",
                    post.id,
                    e
                )
            }
        }

        return createdCount
    }

    private fun createReminderForPost(
        post: Post
    ): Boolean {
        val postId =
            post.id ?: throw IllegalArgumentException(
                "게시글 ID가 존재하지 않습니다."
            )

        val tripGroupId =
            post.author.tripGroup.id
                ?: throw IllegalArgumentException(
                    "여행 그룹 ID가 존재하지 않습니다."
                )

        val receiver = post.author.member

        return notificationService.createIfAbsent(
            receiver = receiver,
            type = NotificationType.POST_REMINDER_ONE_YEAR,
            referenceId = postId,
            title = "1년 전의 여행 추억",
            content = createReminderContent(post),
            targetUrl = "/trips/$tripGroupId/posts/$postId"
        )
    }

    private fun createReminderContent(
        post: Post
    ): String {
        val postContent =
            post.content
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(40)

        return if (postContent == null) {
            "1년 전 작성한 여행 게시글을 다시 확인해보세요."
        } else {
            "1년 전의 추억, \"$postContent\" 게시글을 다시 확인해보세요."
        }
    }

    companion object {
        private val SEOUL_ZONE: ZoneId =
            ZoneId.of("Asia/Seoul")
    }
}