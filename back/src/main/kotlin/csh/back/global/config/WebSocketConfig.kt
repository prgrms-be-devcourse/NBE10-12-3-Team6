package csh.back.global.config

import csh.back.domain.trip.chat.interceptor.StompChannelInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompChannelInterceptor: StompChannelInterceptor,
) : WebSocketMessageBrokerConfigurer {

    @Value("\${cors.allowed-origins}")
    private lateinit var allowedOrigins: List<String>

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/sub", "/queue")
            .setTaskScheduler(heartbeatScheduler())
            .setHeartbeatValue(longArrayOf(HEARTBEAT_MILLIS, HEARTBEAT_MILLIS))
        registry.setApplicationDestinationPrefixes("/pub")
        registry.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompChannelInterceptor)
    }

    @Bean
    fun heartbeatScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 1
        scheduler.setThreadNamePrefix("ws-heartbeat-")
        scheduler.initialize()
        return scheduler
    }

    private companion object {
        const val HEARTBEAT_MILLIS = 10_000L
    }
}
