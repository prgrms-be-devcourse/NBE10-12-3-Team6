package csh.back.global.initData

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

@Profile("dev")
@Configuration
class DevAccountInitData(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Bean
    fun devAccountRunner() = ApplicationRunner {
        createAccountIfMissing(
            email = DEV_EMAIL,
            password = DEV_PASSWORD,
            name = DEV_NAME,
        )
        createAccountIfMissing(
            email = DEV_SECOND_EMAIL,
            password = DEV_SECOND_PASSWORD,
            name = DEV_SECOND_NAME,
        )
    }

    private fun createAccountIfMissing(
        email: String,
        password: String,
        name: String,
    ) {
        if (!memberRepository.existsByEmail(email)) {
            memberRepository.save(
                Member(
                    email,
                    requireNotNull(passwordEncoder.encode(password)),
                    name,
                ),
            )
        }
    }

    companion object {
        //계정 1
        private const val DEV_EMAIL = "dev@example.com"
        private const val DEV_PASSWORD = "1234"
        private const val DEV_NAME = "개발자1"

        //계정 2
        private const val DEV_SECOND_EMAIL = "dev2@example.com"
        private const val DEV_SECOND_PASSWORD = "1234"
        private const val DEV_SECOND_NAME = "개발자2"
    }
}
