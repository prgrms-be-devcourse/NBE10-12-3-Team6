import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct TripTimelineView: View {
    @StateObject private var viewModel: PhotoTimelineViewModel
    @AppStorage("photoDataPreference") private var photoPreference = "balanced"
    @State private var selectedPost: PhotoPost?
    @State private var editingPost: PhotoPost?
    @State private var deletingPost: PhotoPost?
    @State private var uploadDay: Int?

    init(trip: TripDetail, memberID: Int64?) {
        _viewModel = StateObject(wrappedValue: PhotoTimelineViewModel(trip: trip, memberID: memberID))
    }

    private var canUpload: Bool {
        false
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                Text("\(viewModel.trip.name) 타임라인")
                    .font(.title2.bold())

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                ForEach(1...viewModel.trip.dayCount, id: \.self) { dayNumber in
                    PhotoDaySection(
                        dayNumber: dayNumber,
                        group: group(for: dayNumber),
                        pagination: viewModel.dayStates[dayNumber]
                            ?? PhotoDayPaginationState(),
                        memberID: viewModel.memberID,
                        preference: photoPreference,
                        likeStatuses: viewModel.likeStatuses,
                        onTap: { selectedPost = $0 },
                        onLike: { post in Task { await viewModel.toggleLike(post) } },
                        onEdit: { editingPost = $0 },
                        onDelete: { deletingPost = $0 },
                        onRetry: { Task { await viewModel.retryDay(dayNumber) } },
                        onLoadNextPage: {
                            Task { await viewModel.loadNextPage(for: dayNumber) }
                        },
                        canUpload: canUpload,
                        onUpload: { uploadDay = dayNumber }
                    )
                    .task {
                        await viewModel.loadDayIfNeeded(dayNumber)
                    }
                }
            }
            .padding(TripLogDesign.horizontalPadding)
            .padding(.bottom, 40)
        }
        .toolbar {
            if canUpload {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        ForEach(1...viewModel.trip.dayCount, id: \.self) { day in
                            Button("\(day)일차 사진 추가") { uploadDay = day }
                        }
                    } label: { Image(systemName: "camera") }
                }
            }
        }
        .tripLogRefreshable { await viewModel.reloadLoadedDays() }
        .fullScreenCover(item: $selectedPost) { post in
            PhotoLightbox(post: post, preference: photoPreference)
        }
        .sheet(item: Binding(
            get: { uploadDay.map(PhotoUploadTarget.init(dayNumber:)) },
            set: { uploadDay = $0?.dayNumber }
        )) { target in
            PhotoUploadSheet(trip: viewModel.trip, dayNumber: target.dayNumber) {
                uploadDay = nil
                Task { await viewModel.reloadAfterUpload(dayNumber: target.dayNumber) }
            }
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $editingPost) { post in
            EditPhotoContentSheet(post: post) { content in
                let success = await viewModel.update(post, content: content)
                if success { editingPost = nil }
                return success
            }
            .presentationDetents([.height(225)])
            .presentationDragIndicator(.visible)
        }
        .alert(
            "사진 기록 삭제",
            isPresented: Binding(
                get: { deletingPost != nil },
                set: { if !$0 { deletingPost = nil } }
            ),
            presenting: deletingPost
        ) { post in
            Button("취소", role: .cancel) { deletingPost = nil }
            Button("삭제", role: .destructive) {
                deletingPost = nil
                Task { await viewModel.delete(post) }
            }
        } message: { _ in
            Text("이 사진 기록을 삭제할까요?")
        }
    }

    private func group(for dayNumber: Int) -> PhotoDayGroup {
        let date = TripDateFormatter.dayDate(
            startDate: viewModel.trip.startDate,
            dayNumber: dayNumber
        )
        let dateText = TripDateFormatter.api.string(from: date)
        return viewModel.groups.first(where: { $0.date == dateText })
            ?? PhotoDayGroup(date: dateText, posts: [])
    }
}

private struct PhotoUploadTarget: Identifiable {
    let dayNumber: Int
    var id: Int { dayNumber }
}

private struct PhotoDaySection: View {
    let dayNumber: Int
    let group: PhotoDayGroup
    let pagination: PhotoDayPaginationState
    let memberID: Int64?
    let preference: String
    let likeStatuses: [Int64: PhotoLikeStatus]
    let onTap: (PhotoPost) -> Void
    let onLike: (PhotoPost) -> Void
    let onEdit: (PhotoPost) -> Void
    let onDelete: (PhotoPost) -> Void
    let onRetry: () -> Void
    let onLoadNextPage: () -> Void
    let canUpload: Bool
    let onUpload: () -> Void
    @State private var activePostID: Int64?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(dayNumber)일차").font(.headline)
                    Text(displayDate).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if group.posts.count > 1 {
                    Text("\(activeIndex + 1)/\(group.posts.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if canUpload {
                    Button(action: onUpload) { Image(systemName: "camera.badge.ellipsis") }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.circle)
                        .tint(TripLogPalette.blue)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            Divider()

            if !pagination.isLoaded && pagination.errorMessage == nil {
                HStack(spacing: 8) {
                    TripLogLoadingIndicator(size: 16)
                    Text("사진을 불러오는 중...")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            } else if pagination.errorMessage != nil && group.posts.isEmpty {
                Button("다시 불러오기", action: onRetry)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(TripLogPalette.blue)
                    .padding(16)
            } else if group.posts.isEmpty {
                Text("아직 사진 기록이 없습니다.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
            } else {
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 12) {
                        ForEach(Array(group.posts.enumerated()), id: \.element.id) { index, post in
                            PhotoCard(
                                post: post,
                                isOwner: memberID == post.authorMemberId,
                                preference: preference,
                                likeStatus: likeStatuses[post.id],
                                onTap: { onTap(post) },
                                onLike: { onLike(post) },
                                onEdit: { onEdit(post) },
                                onDelete: { onDelete(post) }
                            )
                            .containerRelativeFrame(.horizontal)
                            .id(post.id)
                            .scrollTransition(.interactive, axis: .horizontal) { content, phase in
                                content.opacity(phase.isIdentity ? 1 : 0.88)
                            }
                            .onAppear {
                                if index >= group.posts.count - 2 && pagination.hasNext {
                                    onLoadNextPage()
                                }
                            }
                        }

                        if pagination.isLoading && pagination.isLoaded {
                            TripLogLoadingIndicator(size: 18)
                                .frame(width: 44)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollIndicators(.hidden)
                .scrollTargetBehavior(.paging)
                .scrollPosition(id: $activePostID)
                .padding(16)
                .onAppear {
                    if activePostID == nil {
                        activePostID = group.posts.first?.id
                    }
                }
            }
        }
        .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(TripLogPalette.border))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var activeIndex: Int {
        guard let activePostID,
              let index = group.posts.firstIndex(where: { $0.id == activePostID }) else { return 0 }
        return index
    }

    private var displayDate: String {
        guard let date = TripDateFormatter.date(from: group.date) else { return group.date }
        return TripDateFormatter.display.string(from: date)
    }
}


private struct PhotoCard: View {
    let post: PhotoPost
    let isOwner: Bool
    let preference: String
    let likeStatus: PhotoLikeStatus?
    let onTap: () -> Void
    let onLike: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(post.timeRange) · \(post.confirmedPlaceName ?? "자유 시간")")
                    .font(.caption.bold())
                    .foregroundStyle(post.timelineId == nil ? .secondary : TripLogPalette.blue)
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
                    .layoutPriority(1)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        post.timelineId == nil
                            ? TripLogPalette.surfaceSoft
                            : TripLogPalette.blue.opacity(0.10),
                        in: Capsule()
                    )
                    .overlay {
                        Capsule().stroke(
                            post.timelineId == nil
                                ? TripLogPalette.border
                                : TripLogPalette.blue.opacity(0.22)
                        )
                    }
                Spacer()
                if isOwner {
                    Menu {
                        Button("내용 수정", systemImage: "pencil", action: onEdit)
                        Button(role: .destructive, action: onDelete) {
                            Label {
                                Text("삭제")
                            } icon: {
                                Image(systemName: "trash")
                                    .symbolRenderingMode(.monochrome)
                                    .foregroundStyle(.red)
                            }
                            .foregroundStyle(.red)
                        }
                        .tint(.red)
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundStyle(.secondary)
                            .frame(width: 32, height: 32)
                            .background(TripLogPalette.surfaceSoft, in: Circle())
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
            .padding(.bottom, 8)

            Button(action: onTap) {
                AsyncImage(url: post.previewURL(preference: preference)) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFit()
                    case .failure:
                        ContentUnavailableView("이미지를 불러올 수 없음", systemImage: "photo")
                    default:
                        TripLogLoadingIndicator()
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 210, maxHeight: 320)
                .background(Color(hex: post.dominantColor) ?? .black.opacity(0.2))
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 6))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12)

            TripLogPalette.surfaceMuted
                .frame(height: 12)

            HStack(alignment: .center, spacing: 12) {
                Button(action: onLike) {
                    Label(
                        "\(likeStatus?.likeCount ?? post.likeCount)",
                        systemImage: likeStatus?.liked == true ? "heart.fill" : "heart"
                    )
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(likeStatus?.liked == true ? .red : TripLogPalette.blue)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.capsule)
                .tint(likeStatus?.liked == true ? .red : TripLogPalette.blue)
                .fixedSize()
                .disabled(likeStatus == nil)

                if let content = post.content, !content.isEmpty {
                    Text(content)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.trailing)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                        .allowsTightening(true)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                } else {
                    Spacer(minLength: 0)
                }
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 56)
            .background {
                UnevenRoundedRectangle(
                    topLeadingRadius: 0,
                    bottomLeadingRadius: 15,
                    bottomTrailingRadius: 15,
                    topTrailingRadius: 0,
                    style: .continuous
                )
                .fill(TripLogPalette.blue.opacity(0.08))
            }
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(TripLogPalette.blue.opacity(0.18))
                    .frame(height: 1)
            }
        }
        .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(TripLogPalette.blue.opacity(0.22)))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct PhotoLightbox: View {
    let post: PhotoPost
    let preference: String
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            AsyncImage(url: post.detailURL(preference: preference)) { phase in
                if case .success(let image) = phase {
                    image.resizable().scaledToFit().scaleEffect(scale)
                } else {
                    TripLogLoadingIndicator(size: 26, color: .white)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .gesture(MagnifyGesture().onChanged { scale = max(1, min(4, $0.magnification)) })
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.semibold))
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .controlSize(.small)
            .tint(.white)
            .padding(20)
        }
    }
}

private struct EditPhotoContentSheet: View {
    let post: PhotoPost
    let onSave: (String) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var content: String
    @State private var isSaving = false
    @FocusState private var isContentFocused: Bool

    init(post: PhotoPost, onSave: @escaping (String) async -> Bool) {
        self.post = post
        self.onSave = onSave
        _content = State(initialValue: post.content ?? "")
    }

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 5) {
                TextField("사진과 함께 남길 내용", text: $content, axis: .vertical)
                    .lineLimit(2...3)
                    .focused($isContentFocused)
                    .onChange(of: content) { _, value in content = String(value.prefix(20)) }
                Text("\(content.count)/20")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding(14)
            .glassEffect(.regular.interactive(), in: RoundedRectangle(cornerRadius: 14))
            .tripLogFocusRing(isFocused: isContentFocused, cornerRadius: 14)

            Button {
                isSaving = true
                Task {
                    if await onSave(content) { dismiss() }
                    isSaving = false
                }
            } label: {
                Text("저장")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(.glassProminent)
            .tint(TripLogPalette.blue)
            .disabled(isSaving)
        }
        .padding(.horizontal, 16)
        .padding(.top, 18)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.clear)
    }
}

private extension Color {
    init?(hex: String?) {
        guard let hex else { return nil }
        let value = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        guard value.count == 6, let number = Int(value, radix: 16) else { return nil }
        self.init(
            red: Double((number >> 16) & 0xff) / 255,
            green: Double((number >> 8) & 0xff) / 255,
            blue: Double(number & 0xff) / 255
        )
    }
}
