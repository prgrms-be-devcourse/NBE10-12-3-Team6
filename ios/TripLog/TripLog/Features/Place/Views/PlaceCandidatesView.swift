import Combine
import SwiftUI

struct PlaceCandidatesView: View {
    @StateObject private var viewModel: PlaceCandidatesViewModel
    @ObservedObject private var eventCenter: TripEventNotificationCenter
    @State private var pendingDelete: WishPlace?
    @State private var activeCategory: String?
    private let trip: TripDetail
    private let memberID: Int64?

    init(trip: TripDetail, memberID: Int64?, eventCenter: TripEventNotificationCenter) {
        self.trip = trip
        self.memberID = memberID
        self.eventCenter = eventCenter
        _viewModel = StateObject(wrappedValue: PlaceCandidatesViewModel(trip: trip))
    }

    private var canRegister: Bool {
        TripDateFormatter.status(startDate: trip.startDate, nights: trip.nights) == .before
    }

    private var categoryFilters: [String] {
        let namedCategories = Set(
            viewModel.places
                .map { $0.category.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        ).sorted()
        let hasUncategorized = viewModel.places.contains {
            $0.category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        return namedCategories + (hasUncategorized ? ["기타"] : [])
    }

    private var filteredPlaces: [WishPlace] {
        guard let activeCategory else { return viewModel.places }
        if activeCategory == "기타" {
            return viewModel.places.filter {
                $0.category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
        }
        return viewModel.places.filter {
            $0.category.trimmingCharacters(in: .whitespacesAndNewlines) == activeCategory
        }
    }

    var body: some View {
        ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("후보 장소").font(.title2.bold())
                            Text("일차와 상관없이 여행 전체에서 사용할 후보를 올립니다.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("\(viewModel.places.count)개")
                            .font(.caption.bold())
                            .foregroundStyle(.secondary)
                    }

                    if canRegister {
                        Button {
                            viewModel.isAddPresented = true
                        } label: {
                            Label("후보 올리기", systemImage: "plus")
                        }
                        .buttonStyle(TripLogPrimaryButtonStyle(color: .green))
                    } else {
                        Text("여행이 시작되어 후보 등록이 마감되었습니다")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.green)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color.green.opacity(0.10), in: RoundedRectangle(cornerRadius: 16))
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.green.opacity(0.22)))
                    }

                    if let error = viewModel.errorMessage {
                        Text(error).font(.footnote).foregroundStyle(.red)
                    }

                    if viewModel.isLoading && viewModel.places.isEmpty {
                        TripLogLoadingIndicator(size: 24)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 50)
                    } else if viewModel.places.isEmpty {
                        HStack(spacing: 14) {
                            Text("📍").font(.title2)
                            VStack(alignment: .leading, spacing: 5) {
                                Text("아직 후보 장소가 없습니다.")
                                    .font(.body.weight(.semibold))
                                Text("후보를 올린 뒤 각 일차의 시간 구간에서 투표나 랜덤으로 선택합니다.")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 112, alignment: .leading)
                        .padding(.horizontal, 16)
                        .background(Color.green.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.green.opacity(0.28)))
                    } else {
                        categoryFilterBar

                        ForEach(filteredPlaces) { place in
                            PlaceCandidateCard(
                                place: place,
                                canDelete: canRegister && memberID == place.createdByMemberId
                            ) {
                                pendingDelete = place
                            }
                        }
                    }
                }
                .padding(TripLogDesign.horizontalPadding)
                .padding(.bottom, 100)
        }
        .task {
            await viewModel.load()
            if viewModel.errorMessage == nil {
                eventCenter.markSynchronized(.candidates)
            }
        }
        .tripLogRefreshable { await viewModel.load() }
        .onChange(of: categoryFilters) { _, categories in
            if let activeCategory, !categories.contains(activeCategory) {
                self.activeCategory = nil
            }
        }
        .sheet(isPresented: $viewModel.isAddPresented) {
            PlaceSearchSheet { result in await viewModel.create(result) }
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationContentInteraction(.scrolls)
        }
        .alert(
            "후보 장소 삭제",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            presenting: pendingDelete
        ) { place in
            Button("취소", role: .cancel) { pendingDelete = nil }
            Button("삭제", role: .destructive) {
                pendingDelete = nil
                Task { await viewModel.delete(place) }
            }
        } message: { place in
            Text("'\(place.name)'을(를) 삭제하시겠습니까?")
        }
    }

    private var categoryFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                categoryFilterButton(title: "전체", category: nil)
                ForEach(categoryFilters, id: \.self) { category in
                    categoryFilterButton(title: category, category: category)
                }
            }
            .padding(.vertical, 2)
        }
        .scrollClipDisabled()
    }

    private func categoryFilterButton(title: String, category: String?) -> some View {
        let isSelected = activeCategory == category
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                activeCategory = category
            }
        } label: {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .glassEffect(
                    .regular
                        .tint(isSelected ? TripLogPalette.blue : TripLogPalette.surfaceSoft.opacity(0.55))
                        .interactive(),
                    in: Capsule()
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

private struct PlaceCandidateCard: View {
    let place: WishPlace
    let canDelete: Bool
    let onDelete: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 13) {
            Image(systemName: "mappin.circle.fill")
                .font(.title2)
                .foregroundStyle(.green)
                .frame(width: 42, height: 42)
                .background(Color.green.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 5) {
                Text(place.name).font(.headline)
                if !place.category.isEmpty {
                    Text(place.category).font(.caption).foregroundStyle(.green)
                }
                Text(place.address).font(.subheadline).foregroundStyle(.secondary)
                Text("\(place.createdBy)님이 추가")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            Spacer()
            if canDelete {
                Button("삭제", role: .destructive, action: onDelete)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(TripLogPalette.border))
    }
}

struct PlaceSearchResult: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let address: String
    let category: String
    let externalID: String
    let mapURL: String
}

@MainActor
private final class PlaceSearchViewModel: ObservableObject {
    @Published var query = ""
    @Published var results: [PlaceSearchResult] = []
    @Published var isSearching = false
    @Published var errorMessage: String?
    private let service = PlaceService.shared

    func search() async {
        let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        isSearching = true
        errorMessage = nil
        do {
            let documents = try await service.search(query: value)
            results = documents.map { place in
                return PlaceSearchResult(
                    name: place.placeName,
                    address: place.displayAddress,
                    category: place.categoryGroupName,
                    externalID: place.id,
                    mapURL: place.placeURL
                )
            }
        } catch {
            if !error.isRequestCancellation {
                errorMessage = "카카오맵 장소 검색에 실패했습니다."
            }
        }
        isSearching = false
    }
}

private struct PlaceSearchSheet: View {
    let onSelect: (PlaceSearchResult) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = PlaceSearchViewModel()
    @State private var savingID: UUID?
    @State private var selectedID: UUID?

    var body: some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 7) {
                Text("장소 검색")
                    .font(.subheadline.weight(.semibold))
                HStack(spacing: 8) {
                    TextField(
                        "",
                        text: $viewModel.query,
                        prompt: Text("검색어를 입력해주세요")
                            .foregroundStyle(TripLogPalette.textMuted)
                    )
                        .submitLabel(.search)
                        .onSubmit { Task { await viewModel.search() } }
                        .tripLogInputStyle(height: 48, cornerRadius: 12)
                    Button { Task { await viewModel.search() } } label: {
                        Group {
                            if viewModel.isSearching {
                                TripLogLoadingIndicator(size: 18, lineWidth: 1.8, color: .white)
                            } else {
                                Image(systemName: "magnifyingglass")
                                    .font(.headline)
                            }
                        }
                    }
                    .buttonStyle(.glassProminent)
                    .buttonBorderShape(.circle)
                    .controlSize(.large)
                    .tint(TripLogPalette.blue)
                    .disabled(viewModel.query.trimmingCharacters(in: .whitespaces).isEmpty || viewModel.isSearching)
                }
            }
            .padding(.horizontal, 16)

            ScrollView {
                LazyVStack(spacing: 8) {
                if let error = viewModel.errorMessage {
                    Text(error).foregroundStyle(.red)
                }
                ForEach(viewModel.results) { result in
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(result.name).font(.subheadline.bold()).foregroundStyle(.primary)
                            Text(result.address).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            if !result.category.isEmpty {
                                Text(result.category).font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        if selectedID == result.id {
                            Button {
                                savingID = result.id
                                Task {
                                    if await onSelect(result) { dismiss() }
                                    savingID = nil
                                }
                            } label: {
                                Group {
                                    if savingID == result.id {
                                        TripLogLoadingIndicator(size: 18, lineWidth: 1.8, color: .white)
                                    } else {
                                        Text("등록").font(.subheadline.bold())
                                    }
                                }
                                .frame(width: 54, height: 36)
                            }
                            .buttonStyle(.glassProminent)
                            .tint(TripLogPalette.blue)
                            .disabled(savingID != nil)
                        }
                    }
                    .padding(12)
                    .background(
                        selectedID == result.id ? TripLogPalette.blue.opacity(0.08) : TripLogPalette.surface,
                        in: RoundedRectangle(cornerRadius: 12)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(selectedID == result.id ? TripLogPalette.blue : TripLogPalette.border)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .onTapGesture {
                        selectedID = result.id
                    }
                }
                }
                .padding(.vertical, 12)
            }
            .padding(.horizontal, 16)
            .overlay {
                if viewModel.results.isEmpty && !viewModel.isSearching {
                    Text("이름이나 주소를 검색해보세요.")
                        .font(.subheadline)
                        .foregroundStyle(TripLogPalette.textMuted)
                }
            }
        }
        .padding(.top, 28)
        .background(Color.clear)
    }
}
