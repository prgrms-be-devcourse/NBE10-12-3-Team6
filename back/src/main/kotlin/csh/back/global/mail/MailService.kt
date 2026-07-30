package csh.back.global.mail

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val fromAddress: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val FROM_DISPLAY_NAME = "Triplog"
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
                  <tr>
                    <td style="background-color:#4A90E2;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">
                        <img src="https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/svg/1f392.svg" alt="" style="width:24px;height:24px;vertical-align:middle;margin-right:6px;">
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
