package csh.back.global.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FirebaseConfig {

    @Bean
    fun firebaseApp(): FirebaseApp {
        val existingApp = FirebaseApp.getApps().firstOrNull()

        if (existingApp != null) {
            return existingApp
        }

        val options = FirebaseOptions.builder()
            .setCredentials(
                GoogleCredentials.getApplicationDefault()
            )
            .build()

        return FirebaseApp.initializeApp(options)
    }

    @Bean
    fun firebaseMessaging(
        firebaseApp: FirebaseApp
    ): FirebaseMessaging {
        return FirebaseMessaging.getInstance(firebaseApp)
    }
}