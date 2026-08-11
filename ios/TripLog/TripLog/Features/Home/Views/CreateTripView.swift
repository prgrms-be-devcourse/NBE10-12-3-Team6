import SwiftUI

struct CreateTripView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var region = ""
    @State private var startDate = Date()
    @State private var nights = 2
    @State private var isSaving = false

    let onCreate: (String, String, Date, Int) async -> Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                labeledField("여행 이름") {
                    TextField(
                        "",
                        text: $name,
                        prompt: Text("여행 이름을 입력해주세요").foregroundStyle(TripLogPalette.textMuted)
                    )
                        .tripLogInputStyle(height: 52, cornerRadius: 13)
                }

                labeledField("지역") {
                    RegionPickerField(region: $region)
                }

                labeledField("시작일") {
                    if Calendar.current.isDateInToday(startDate) {
                        Text("오늘 출발 시 계획 등록 제한")
                            .font(.caption2.bold())
                            .foregroundStyle(.orange)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.orange.opacity(0.10), in: Capsule())
                    }
                    DatePicker("날짜를 선택", selection: $startDate, displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .environment(\.locale, Locale(identifier: "ko_KR"))
                        .padding(.horizontal, 14)
                        .frame(height: 52)
                        .glassControl(in: RoundedRectangle(cornerRadius: 13))
                }

                labeledField("기간") {
                    HStack(spacing: 16) {
                        rangeButton(icon: "minus") { nights = max(0, nights - 1) }
                        Text("\(nights)박 \(nights + 1)일")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity)
                        rangeButton(icon: "plus") { nights = min(10, nights + 1) }
                    }
                }

                Button {
                    Task {
                        isSaving = true
                        if await onCreate(name, region, startDate, nights) { dismiss() }
                        isSaving = false
                    }
                } label: {
                    Group {
                        if isSaving {
                            TripLogLoadingIndicator(color: .white)
                        } else {
                            Text("여행 모임 만들기")
                        }
                    }
                    .font(.body.bold())
                    .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.glassProminent)
                .tint(TripLogPalette.blue)
                .disabled(!canCreate)
                .opacity(canCreate ? 1 : 0.7)
            }
            .padding(.horizontal, 16)
            .padding(.top, 44)
            .padding(.bottom, 20)
        }
        .background(Color.clear)
    }

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !region.trimmingCharacters(in: .whitespaces).isEmpty && !isSaving
    }

    private func labeledField<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.weight(.semibold))
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func rangeButton(icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.body.bold())
                .foregroundStyle(TripLogPalette.blue)
                .frame(width: 40, height: 40)
                .glassEffect(
                    .regular.tint(TripLogPalette.surfaceSoft.opacity(0.78)).interactive(),
                    in: Circle()
                )
                .overlay(Circle().stroke(TripLogPalette.border))
        }
        .buttonStyle(.plain)
    }
}

private struct RegionPickerField: View {
    @Binding var region: String
    @State private var isSheetPresented = false
    @FocusState private var isRegionFocused: Bool

    private var suggestions: [String] {
        let query = region.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }
        return RegionCatalog.groups.flatMap(\.cities)
            .filter { $0.localizedCaseInsensitiveContains(query) || query.localizedCaseInsensitiveContains($0) }
            .prefix(8)
            .map { $0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.secondary)
                    TextField(
                        "",
                        text: $region,
                        prompt: Text("지역 검색 또는 직접 입력").foregroundStyle(TripLogPalette.textMuted)
                    )
                    .foregroundStyle(.primary)
                    .focused($isRegionFocused)
                }
                .padding(.horizontal, 12)
                .frame(height: 52)
                .glassEffect(
                    .regular
                        .tint(TripLogPalette.surfaceSoft.opacity(0.45))
                        .interactive(),
                    in: RoundedRectangle(cornerRadius: 13, style: .continuous)
                )
                .tripLogFocusRing(isFocused: isRegionFocused, cornerRadius: 13)

                Button { isSheetPresented = true } label: {
                    Image(systemName: "list.bullet")
                        .foregroundStyle(TripLogPalette.blue)
                        .frame(width: 52, height: 52)
                        .glassEffect(
                            .regular.tint(TripLogPalette.surfaceSoft.opacity(0.78)).interactive(),
                            in: RoundedRectangle(cornerRadius: 13)
                        )
                        .overlay(RoundedRectangle(cornerRadius: 13).stroke(TripLogPalette.border))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("전체 지역 목록")
            }

            if !suggestions.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(suggestions, id: \.self) { city in
                            Button(city) { region = city }
                                .font(.caption.bold())
                                .foregroundStyle(region == city ? .white : .primary)
                                .padding(.horizontal, 12)
                                .frame(height: 34)
                                .background(region == city ? TripLogPalette.blue : TripLogPalette.surface)
                                .clipShape(RoundedRectangle(cornerRadius: 11))
                                .overlay(RoundedRectangle(cornerRadius: 11).stroke(region == city ? Color.clear : TripLogPalette.border))
                        }
                    }
                }
            } else {
                Text("지역명을 입력하면 추천 지역이 나타나요")
                    .font(.caption2)
                    .foregroundStyle(TripLogPalette.textSoft)
                    .frame(maxWidth: .infinity)
                    .frame(height: 34)
                    .overlay(RoundedRectangle(cornerRadius: 11).stroke(TripLogPalette.border))
            }
        }
        .sheet(isPresented: $isSheetPresented) {
            RegionCatalogSheet(selection: $region)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }
}

private extension View {
    func glassControl<S: Shape>(in shape: S) -> some View {
        glassEffect(
            .regular.tint(TripLogPalette.surfaceSoft.opacity(0.78)).interactive(),
            in: shape
        )
        .overlay(shape.stroke(TripLogPalette.border, lineWidth: 1))
    }
}

private enum RegionCatalog {
    struct Group: Identifiable {
        let name: String
        let cities: [String]
        var id: String { name }
    }

    static let groups: [Group] = [
        Group(name: "수도권", cities: ["서울", "인천", "수원", "경기"]),
        Group(name: "강원", cities: ["강릉", "속초", "춘천", "동해", "원주", "평창"]),
        Group(name: "충청", cities: ["대전", "청주", "천안", "세종", "공주", "충주"]),
        Group(name: "전라", cities: ["광주", "전주", "여수", "순천", "목포", "군산"]),
        Group(name: "경상", cities: ["부산", "대구", "울산", "경주", "거제", "통영", "진주", "포항", "안동"]),
        Group(name: "제주", cities: ["제주", "서귀포"]),
    ]
}

private struct RegionCatalogSheet: View {
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 12) {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    ForEach(RegionCatalog.groups) { group in
                        VStack(alignment: .leading, spacing: 10) {
                            Text(group.name)
                                .font(.caption.bold())
                                .foregroundStyle(.secondary)
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 74), spacing: 8)], spacing: 8) {
                                ForEach(group.cities, id: \.self) { city in
                                    Button(city) {
                                        selection = city
                                        dismiss()
                                    }
                                    .font(.subheadline.bold())
                                    .foregroundStyle(selection == city ? .white : .primary)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 40)
                                    .background(selection == city ? TripLogPalette.blue : TripLogPalette.surfaceSoft)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }
        .padding(.top, 28)
        .background(Color.clear)
    }
}
