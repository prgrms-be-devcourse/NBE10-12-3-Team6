package csh.back.global.mail

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class MailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val fromAddress: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val FROM_DISPLAY_NAME = "Triplog"
        // 이메일 헤더 아이콘 리소스 경로 (src/main/resources/static/mail/ 하위 → classpath: static/mail/...)
        private const val ICON_CLASSPATH = "static/mail/triplog-icon.png"
    }

    // 이메일 헤더 아이콘을 classpath에서 읽어 base64 data URI로 캐싱.
    // 왜 URL이 아닌 data URI인가:
    //   외부 URL(jsdelivr 등)은 리포지토리 push + 브랜치 머지가 되어야 수신자의 이메일 클라이언트가 fetch 가능.
    //   data URI는 이미지 바이트를 HTML에 통째로 임베드해 외부 요청 자체가 없음 → 로컬 개발 중에도 정상 렌더.
    // by lazy:
    //   첫 이메일 발송 시점에 한 번만 파일 IO + base64 인코딩. 이후 발송에서는 캐시된 문자열 재사용.
    // ClassPathResource:
    //   JAR로 빌드/배포된 후에도 동일하게 리소스에 접근 가능 (파일시스템 경로 아님)
    private val iconDataUri: String by lazy {
        val bytes = ClassPathResource(ICON_CLASSPATH).inputStream.use { it.readBytes() }
        "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    // @Async: mailExecutor 풀에서 실행, 호출 스레드는 즉시 반환
    @Async("mailExecutor")
    fun sendHtmlEmail(to: String, subject: String, contentHtml: String) {
        val message: MimeMessage = mailSender.createMimeMessage()
        // false: 멀티파트 아님, UTF-8: 한글 깨짐 방지
        val helper = MimeMessageHelper(message, false, "UTF-8")

        // 수신자 메일함에 "Triplog <계정>" 로 표시
        helper.setFrom(fromAddress, FROM_DISPLAY_NAME)
        helper.setTo(to)
        helper.setSubject(subject)
        // true: HTML 형식으로 전송
        helper.setText(wrapWithLayout(contentHtml), true)

        try {
            mailSender.send(message)
        } catch (e: Exception) {
            // @Async void 메서드는 예외가 유실되므로 여기서 명시적으로 로깅
            log.error("mail send failed to={}", to, e)
        }
    }

    // 공용 레이아웃(헤더/푸터/테두리)으로 본문 콘텐츠 감싸기
    private fun wrapWithLayout(contentHtml: String) = """
        <!DOCTYPE html>
        <html lang="ko">
        <head><meta charset="UTF-8"></head>
        <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:'Apple SD Gothic Neo',Arial,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:40px 0;">
            <tr>
              <td align="center">
                <table width="520" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                  <!-- 헤더 -->
                  <!-- 아이콘: 프론트 랜딩 페이지의 배낭 SVG를 흰 stroke으로 PNG 변환한 파일(static/mail/triplog-icon.png)을
                       base64 data URI로 임베드. 외부 CDN에 의존하지 않아 로컬 개발 중에도 그대로 렌더됨.
                       PNG 자체가 #4A90E2 배경(헤더 배경색과 동일)이라 헤더에 얹으면 흰 아이콘만 도드라져 보임. -->
                  <tr>
                    <td style="background-color:#4A90E2;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:30px;font-weight:700;letter-spacing:-0.5px;">
                        <img src="$iconDataUri" alt="" style="width:46px;height:46px;vertical-align:middle;margin-right:10px;border:0;display:inline-block;">
                        Triplog
                      </h1>
                    </td>
                  </tr>

                  <!-- 본문 -->
                  <tr>
                    <td style="padding:40px 40px 32px;">
                      $contentHtml
                    </td>
                  </tr>

                  <!-- 푸터 -->
                  <tr>
                    <td style="background-color:#f9f9f9;padding:20px 40px;text-align:center;border-top:1px solid #eeeeee;">
                      <p style="margin:0;color:#cccccc;font-size:12px;">© 2026 Triplog. All rights reserved.</p>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
    """.trimIndent()
}
