package csh.back.domain.trip.post.reminder.service

import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.reminder.entity.PostReminder
import csh.back.domain.trip.post.reminder.repository.PostReminderRepository
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.global.push.dto.PushMessage
import csh.back.global.push.service.FirebasePushSender
import csh.back.global.push.service.PushTokenService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PostReminderSender(
    private val postRepository: PostRepository,
    private val postReminderRepository: PostReminderRepository,
    private val pushTokenService: PushTokenService,
    private val firebasePushSender: FirebasePushSender
) {

    private val log =
        LoggerFactory.getLogger(javaClass)

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    fun send(
        postId: Long
    ) {
        if (
            postReminderRepository
                .existsByPostId(postId)
        ) {
            return
        }

        val post =
            postRepository
                .findReminderPostById(postId)
                ?: throw IllegalArgumentException(
                    "리마인드 대상 게시글이 " +
                            "존재하지 않습니다. postId=$postId"
                )

        val reminder =
            postReminderRepository.save(
                PostReminder.create(post)
            )

        val memberId =
            requireNotNull(
                post.author.member.id
            ) {
                "게시글 작성자의 회원 ID가 " +
                        "존재하지 않습니다."
            }

        val tripGroupId =
            requireNotNull(
                post.author.tripGroup.id
            ) {
                "여행 그룹 ID가 존재하지 않습니다."
            }

        val pushTokens =
            pushTokenService
                .getActiveTokens(memberId)

        if (pushTokens.isEmpty()) {
            reminder.markFailed(
                "게시글 작성자의 활성 FCM 토큰이 없습니다."
            )
            return
        }

        val pushMessage =
            PushMessage(
                title = "1년 전 여행을 기억하시나요?",
                body = createMessageBody(post),
                targetUrl =
                    "/trips/$tripGroupId/posts/$postId",
                postId = postId
            )

        val messageIds =
            pushTokens.mapNotNull { pushToken ->
                firebasePushSender.send(
                    fcmToken = pushToken.token,
                    pushMessage = pushMessage
                )
            }

        if (messageIds.isEmpty()) {
            reminder.markFailed(
                "등록된 모든 기기로의 " +
                        "푸시 전송에 실패했습니다."
            )
            return
        }

        reminder.markSent(
            messageIds.first()
        )

        log.info(
            "포스트 리마인드 전송 완료. " +
                    "postId={}, 성공 기기={}, 전체 기기={}",
            postId,
            messageIds.size,
            pushTokens.size
        )
    }

    private fun createMessageBody(
        post: Post
    ): String {
        val preview =
            post.content
                ?.replace("\n", " ")
                ?.trim()
                ?.take(40)

        return if (preview.isNullOrBlank()) {
            "1년 전에 남긴 여행 기록을 " +
                    "다시 확인해보세요."
        } else {
            "\"$preview\" 여행 기록을 " +
                    "다시 확인해보세요."
        }
    }
}