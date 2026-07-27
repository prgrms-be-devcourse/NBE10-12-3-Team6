package csh.back.global.jwt;

// 쿠키 이름을 한 곳에서 관리 (오타/불일치 방지)
public class CookieNames {
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";

    private CookieNames() {
    }
}