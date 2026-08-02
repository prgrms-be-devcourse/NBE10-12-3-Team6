package csh.back.global.push.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import csh.back.global.push.dto.PushMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FirebasePushSender(
    private val firebaseMessaging: FirebaseMessaging
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun send(
        fcmToken: String,
        pushMessage: PushMessage
    ): String? {
        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(
                Notification.builder()
                    .setTitle(pushMessage.title)
                    .setBody(pushMessage.body)
                    .build()
            )
            .putData("type", "POST_REMINDER")
            .putData("postId", pushMessage.postId.toString())
            .putData("targetUrl", pushMessage.targetUrl)
            .build()

        return try {
            val messageId = firebaseMessaging.send(message)

            log.info(
                "FCM 푸시 전송 성공. postId={}, messageId={}",
                pushMessage.postId,
                messageId
            )

            messageId
        } catch (exception: FirebaseMessagingException) {
            log.error(
                "FCM 푸시 전송 실패. postId={}, errorCode={}",
                pushMessage.postId,
                exception.messagingErrorCode,
                exception
            )

            null
        }
    }
}