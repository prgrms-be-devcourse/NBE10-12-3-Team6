import SwiftUI

struct JoinTripView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var code = ""
    @State private var isJoining = false

    let onJoin: (String) async -> Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Image(systemName: "link.circle.fill")
                    .font(.system(size: 54))
                    .foregroundStyle(.blue)

                Text("초대 코드로 참여")
                    .font(.title2.bold())

                TextField("초대 코드", text: $code)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .tripLogInputStyle(height: 52, cornerRadius: 13)

                Button {
                    Task {
                        isJoining = true
                        if await onJoin(code.uppercased()) { dismiss() }
                        isJoining = false
                    }
                } label: {
                    Group {
                        if isJoining {
                            TripLogLoadingIndicator(color: .white)
                        } else {
                            Text("여행방 참여")
                        }
                    }
                    .font(.body.bold())
                    .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.glassProminent)
                .tint(TripLogPalette.blue)
                .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty || isJoining)

                Spacer()
            }
            .padding(24)
            .navigationTitle("여행 참여")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                }
            }
        }
    }
}
