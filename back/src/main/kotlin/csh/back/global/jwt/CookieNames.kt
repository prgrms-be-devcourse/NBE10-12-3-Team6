package csh.back.global.jwt

// 쿠키 이름을 한 곳에서 관리 (오타/불일치 방지)
object CookieNames {
    const val ACCESS_TOKEN = "accessToken"
    const val REFRESH_TOKEN = "refreshToken"
}