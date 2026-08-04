import { NextResponse, type NextRequest } from "next/server";

// 인증이 필요 없는 페이지 prefix. 이 목록에 없는 모든 페이지는 로그인 필수.
// 화이트리스트 방식을 쓰는 이유: 새 protected 페이지를 추가할 때 미들웨어를 수정할 필요가 없음.
const PUBLIC_PATH_PREFIXES = ["/forgot-password", "/invite"];

// 로그인 상태에서 접근하면 홈으로 튕겨야 하는 페이지 (로그인/회원가입 랜딩).
const AUTH_LANDING_PATHS = ["/"];

// 백엔드 JwtAuthenticationFilter가 두 쿠키 중 하나라도 있으면 인증 시도하므로 동일하게 판단.
// httpOnly 쿠키라 JS로는 못 읽지만 서버 미들웨어에서는 request.cookies로 접근 가능.
function isAuthenticated(request: NextRequest): boolean {
  const access = request.cookies.get("accessToken")?.value;
  const refresh = request.cookies.get("refreshToken")?.value;
  return Boolean(access || refresh);
}

function isPublicPath(pathname: string): boolean {
  if (AUTH_LANDING_PATHS.includes(pathname)) return true;
  return PUBLIC_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const authed = isAuthenticated(request);

  // 로그인 상태에서 로그인 랜딩(/) 접근 → 홈으로. 이미 로그인한 사람에게 로그인 화면 보이면 UX 어색함.
  if (authed && AUTH_LANDING_PATHS.includes(pathname)) {
    return NextResponse.redirect(new URL("/home", request.url));
  }

  // 미인증 + protected 경로 접근 → 로그인 페이지로.
  // 쿠키 유효성/멤버십 검증은 미들웨어에서 못 하므로 apiFetch의 401/403 처리가 2차 방어선.
  if (!authed && !isPublicPath(pathname)) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  return NextResponse.next();
}

// 정적 파일/이미지/API/PWA 매니페스트는 미들웨어에서 제외.
// 확장자 있는 파일(.png, .ico, .svg 등)도 제외해서 정적 리소스 요청은 그대로 통과.
export const config = {
  matcher: [
    "/((?!api|_next/static|_next/image|favicon.ico|manifest.json|apple-icon.png|.*\\..*).*)",
  ],
};
