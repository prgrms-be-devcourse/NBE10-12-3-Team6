import SwiftUI
import UIKit

struct RecoveryCodeView: View {
    let code: String
    let onDone: () -> Void
    @State private var copied = false
    @State private var isRevealed = false

    var body: some View {
        VStack(spacing: 0) {
            TripLogFormHeader("회원가입 완료")

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                Text("비밀번호를 잊었을 때 계정을 되찾기 위한 본인 확인 코드가 발급됐어요.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                VStack(spacing: 12) {
                    Text("본인 확인 코드")
                        .font(.caption)
                        .foregroundStyle(TripLogPalette.textMuted)

                    HStack(spacing: 12) {
                        Text(isRevealed ? displayCode : maskedCode)
                            .font(.system(size: 28, weight: .bold, design: .monospaced))
                            .tracking(4)
                            .foregroundStyle(TripLogPalette.blue)
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)

                        Button {
                            isRevealed.toggle()
                        } label: {
                            Image(systemName: isRevealed ? "eye.slash" : "eye")
                                .font(.system(size: 20, weight: .semibold))
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.circle)
                        .tint(TripLogPalette.blue)
                        .accessibilityLabel(isRevealed ? "코드 가리기" : "코드 보기")
                    }

                    Button {
                        UIPasteboard.general.string = code
                        copied = true
                    } label: {
                        Label(copied ? "복사됐어요" : "복사하기", systemImage: copied ? "checkmark" : "doc.on.doc")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .frame(height: 30)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.capsule)
                    .tint(copied ? .green : TripLogPalette.blue)
                }
                .frame(maxWidth: .infinity)
                .padding(24)
                .background(Color.blue.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(TripLogPalette.blue.opacity(0.45), lineWidth: 2))
                .padding(.top, 32)

                VStack(alignment: .leading, spacing: 8) {
                    Text("⚠️ 반드시 이 화면에서 저장해 주세요")
                        .font(.subheadline.bold())
                    Text("• 주위에 사람이 없는 곳에서 화면을 캡처하거나 메모장에 기록해 두세요.")
                    Text("• 이 코드는 지금 이후로 다시 볼 수 없으며 서버에도 원본이 저장되지 않습니다.")
                    Text("• 비밀번호를 잊었을 때 이 코드로만 계정을 되찾을 수 있습니다.")
                }
                .font(.caption)
                .foregroundStyle(Color(red: 161 / 255, green: 98 / 255, blue: 7 / 255))
                .lineSpacing(3)
                .padding(16)
                .background(Color.yellow.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.yellow.opacity(0.35)))
                .padding(.top, 24)

                Button("저장했어요, 확인", action: onDone)
                    .buttonStyle(TripLogPrimaryButtonStyle(color: TripLogPalette.blue))
                    .padding(.top, 24)
                }
                .padding(.horizontal, 24)
                .padding(.top, 28)
                .padding(.bottom, 40)
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea(edges: .bottom))
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
    }

    private var displayCode: String {
        code.count == 6 ? "\(code.prefix(3)) \(code.suffix(3))" : code
    }

    private var maskedCode: String {
        String(displayCode.map { $0 == " " ? " " : "●" })
    }
}
