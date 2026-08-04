package csh.back.global.mail

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
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
        // 이메일 헤더 아이콘 리소스 경로 (src/main/resources/static/mail/ 하위 → classpath: static/mail/...)
        private const val ICON_CLASSPATH = "static/mail/triplog-icon.png"
        // HTML의 <img src="cid:..."> 참조자. addInline() 등록 이름과 반드시 동일해야 함.
        private const val ICON_CID = "triplog-icon"
    }

    // @Async: mailExecutor 풀에서 실행, 호출 스레드는 즉시 반환
    @Async("mailExecutor")
    fun sendHtmlEmail(to: String, subject: String, contentHtml: String) {
        val message: MimeMessage = mailSender.createMimeMessage()
        // multipart=true: HTML 본문 + 인라인 이미지(아이콘)를 함께 담기 위해 필수.
        // false로 두면 addInline() 호출 시 IllegalStateException.
        val helper = MimeMessageHelper(message, true, "UTF-8")

        // 수신자 메일함에 "Triplog <계정>" 로 표시
        helper.setFrom(fromAddress, FROM_DISPLAY_NAME)
        helper.setTo(to)
        helper.setSubject(subject)
        // true: HTML 형식으로 전송
        helper.setText(wrapWithLayout(contentHtml), true)

        // 아이콘을 cid 인라인 첨부로 붙임.
        // 왜 data URI가 아닌 cid인가:
        //   Gmail은 보안(피싱/XSS 방지) 정책상 <img src="data:..."> 를 프록시 리라이팅 과정에서 제거함
        //   → 수신자에게 아이콘이 아예 안 보임(Naver·다음은 관대해서 렌더됨).
        //   cid 인라인은 이미지 바이트를 메일 자체에 첨부하고 <img src="cid:xxx">로 참조하는 표준 방식이라
        //   Gmail/Outlook/Naver 등 주요 클라이언트 모두에서 렌더링됨. 외부 CDN 요청도 없음.
        helper.addInline(ICON_CID, ClassPathResource(ICON_CLASSPATH))

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
                       cid 인라인 첨부로 실어보냄 (sendHtmlEmail의 addInline 참고).
                       PNG 자체가 #4A90E2 배경(헤더 배경색과 동일)이라 헤더에 얹으면 흰 아이콘만 도드라져 보임. -->
                  <tr>
                    <td style="background-color:#4A90E2;padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:30px;font-weight:700;letter-spacing:-0.5px;">
                        <img src="cid:$ICON_CID" alt="" style="width:46px;height:46px;vertical-align:middle;margin-right:10px;border:0;display:inline-block;">
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
