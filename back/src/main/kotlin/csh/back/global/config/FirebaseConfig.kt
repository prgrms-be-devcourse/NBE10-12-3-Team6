package csh.back.global.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "firebase",
    name = ["enabled"],
    havingValue = "true"
)
class FirebaseConfig {

    private val log =
        LoggerFactory.getLogger(javaClass)

    @Bean
    fun firebaseApp(): FirebaseApp {
        val existingApp =
            FirebaseApp.getApps()
                .firstOrNull()

        if (existingApp != null) {
            return existingApp
        }

        val credentials =
            GoogleCredentials
                .getApplicationDefault()

        val options =
            FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()

        val firebaseApp =
            FirebaseApp.initializeApp(options)

        log.info(
            "Firebase Admin SDK 초기화 완료. name={}",
            firebaseApp.name
        )

        return firebaseApp
    }

    @Bean
    fun firebaseMessaging(
        firebaseApp: FirebaseApp
    ): FirebaseMessaging {
        return FirebaseMessaging
            .getInstance(firebaseApp)
    }
}