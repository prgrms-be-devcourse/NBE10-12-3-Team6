import SwiftUI

struct AuthLandingView: View {
    let errorMessage: String?
    let onLogin: () -> Void
    let onSignup: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 18) {
                Image(systemName: "backpack")
                    .font(.system(size: 58, weight: .medium))
                    .foregroundStyle(TripLogPalette.blue)

                Text("TripLog")
                    .font(.system(size: 36, weight: .bold))

                Text("친구들과 여행을 계획하고,\n여행 중 순간을 기록해보세요.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(5)
            }

            Spacer()

            VStack(spacing: 14) {
                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                }

                Button("로그인하기", action: onLogin)
                    .buttonStyle(TripLogPrimaryButtonStyle(color: TripLogPalette.blue))

                Button("계정이 없으신가요? 회원가입", action: onSignup)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .underline()

            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 18)
        .background(TripLogPalette.background)
    }
}

#Preview {
    AuthLandingView(errorMessage: nil, onLogin: {}, onSignup: {})
}
