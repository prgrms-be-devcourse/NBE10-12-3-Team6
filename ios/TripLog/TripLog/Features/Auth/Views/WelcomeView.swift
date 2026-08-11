import SwiftUI

struct WelcomeView: View {
    @State private var isVisible = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [.blue.opacity(0.75), .blue, .indigo],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            HStack(spacing: 8) {
                Text("환영합니다")
                    .font(.system(size: 29, weight: .bold))
                Text("👋")
                    .font(.system(size: 29))
            }
            .foregroundStyle(.white)
            .opacity(isVisible ? 1 : 0)
            .offset(y: isVisible ? 0 : 10)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5)) {
                isVisible = true
            }
        }
    }
}

#Preview {
    WelcomeView()
}
