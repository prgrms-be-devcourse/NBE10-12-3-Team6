package csh.back.global.push.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import csh.back.global.push.dto.PushMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class FirebasePushSender(
    private val firebaseMessagingProvider:
    ObjectProvider<FirebaseMessaging>,
    private val pushTokenService:
    PushTokenService
) {

    private val log =
        LoggerFactory.getLogger(javaClass)

    fun send(
        fcmToken: String,
        pushMessage: PushMessage
    ): String? {
        val firebaseMessaging =
            firebaseMessagingProvider
                .ifAvailable

        if (firebaseMessaging == null) {
            log.warn(
                "Firebase가 비활성화되어 있습니다. " +
                        "postId={}",
                pushMessage.postId
            )
            return null
        }

        val message =
            Message.builder()
                .setToken(fcmToken)
                .setNotification(
                    Notification.builder()
                        .setTitle(
                            pushMessage.title
                        )
                        .setBody(
                            pushMessage.body
                        )
                        .build()
                )
                .putData(
                    "type",
                    "POST_REMINDER_ONE_YEAR"
                )
                .putData(
                    "postId",
                    pushMessage.postId.toString()
                )
                .putData(
                    "targetUrl",
                    pushMessage.targetUrl
                )
                .build()

        return try {
            val messageId =
                firebaseMessaging.send(message)

            log.info(
                "FCM 푸시 전송 성공. " +
                        "postId={}, messageId={}",
                pushMessage.postId,
                messageId
            )

            messageId
        } catch (
            exception: FirebaseMessagingException
        ) {
            handleFirebaseException(
                fcmToken = fcmToken,
                pushMessage = pushMessage,
                exception = exception
            )

            null
        } catch (
            exception: RuntimeException
        ) {
            log.error(
                "FCM 푸시 처리 중 오류가 발생했습니다. " +
                        "postId={}",
                pushMessage.postId,
                exception
            )

            null
        }
    }

    private fun handleFirebaseException(
        fcmToken: String,
        pushMessage: PushMessage,
        exception: FirebaseMessagingException
    ) {
        val errorCode =
            exception.messagingErrorCode

        if (isInvalidTokenError(errorCode)) {
            pushTokenService.deactivate(
                fcmToken
            )

            log.warn(
                "유효하지 않은 FCM 토큰을 " +
                        "비활성화했습니다. postId={}, errorCode={}",
                pushMessage.postId,
                errorCode
            )
        }

        log.error(
            "FCM 푸시 전송 실패. " +
                    "postId={}, errorCode={}",
            pushMessage.postId,
            errorCode,
            exception
        )
    }

    private fun isInvalidTokenError(
        errorCode: MessagingErrorCode?
    ): Boolean {
        return errorCode ==
                MessagingErrorCode.UNREGISTERED ||
                errorCode ==
                MessagingErrorCode.INVALID_ARGUMENT
    }
}