package csh.back.global.jwt

// 쿠키 이름을 한 곳에서 관리 (오타/불일치 방지)
object CookieNames {
    const val ACCESS_TOKEN = "accessToken"
    const val REFRESH_TOKEN = "refreshToken"
    // 로그아웃해도 유지되는 기기 식별자 — 재로그인 시 "알던 기기인지" 판단에 사용
    const val DEVICE_ID = "device_id"
}