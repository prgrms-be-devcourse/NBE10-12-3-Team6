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

    // 비밀번호 재설정 링크 이메일 — 링크는 프론트의 재설정 페이지 URL
    fun sendPasswordResetEmail(to: String, resetLink: String, ttlMinutes: Long) {
        sendHtmlEmail(
            to = to,
            subject = "[Triplog] 비밀번호 재설정 안내",
            contentHtml = buildPasswordResetContent(resetLink, ttlMinutes),
        )
    }

    private fun buildPasswordResetContent(resetLink: String, ttlMinutes: Long) = """
        <p style="margin:0 0 8px;color:#333333;font-size:18px;font-weight:600;">비밀번호 재설정</p>
        <p style="margin:0 0 24px;color:#888888;font-size:14px;line-height:1.6;">
          아래 버튼을 눌러 새 비밀번호를 설정해 주세요.<br>
          이 링크는 <strong>${ttlMinutes}분</strong> 후 만료됩니다.
        </p>

        <div style="text-align:center;margin-bottom:32px;">
          <a href="$resetLink"
             style="display:inline-block;padding:14px 32px;background-color:#4A90E2;color:#ffffff;text-decoration:none;border-radius:8px;font-size:15px;font-weight:600;">
            비밀번호 재설정
          </a>
        </div>

        <p style="margin:0 0 8px;color:#aaaaaa;font-size:12px;line-height:1.6;">
          버튼이 열리지 않으면 아래 주소를 브라우저에 붙여 넣어 주세요.
        </p>
        <p style="margin:0 0 24px;color:#4A90E2;font-size:12px;word-break:break-all;">
          $resetLink
        </p>

        <p style="margin:0;padding:16px;background-color:#fff3cd;border-radius:8px;color:#856404;font-size:13px;line-height:1.6;">
          ⚠️ 본인이 요청하지 않았다면 이 메일을 무시해 주세요. 계정은 안전합니다.
        </p>
    """.trimIndent()

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
