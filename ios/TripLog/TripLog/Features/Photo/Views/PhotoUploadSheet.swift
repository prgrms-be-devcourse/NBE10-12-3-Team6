import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct PhotoUploadSheet: View {
    let trip: TripDetail
    let dayNumber: Int
    let embedded: Bool
    let onShowAll: () -> Void
    let onUploaded: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selectedItem: PhotosPickerItem?
    @State private var isPhotoSourcePresented = false
    @State private var isPhotoLibraryPresented = false
    @State private var isCameraPresented = false
    @State private var imageData: Data?
    @State private var previewImage: UIImage?
    @State private var filename = "photo.jpg"
    @State private var mimeType = "image/jpeg"
    @State private var content = ""
    @State private var slot: CurrentPhotoSlot?
    @State private var isLoading = true
    @State private var isUploading = false
    @State private var uploadProgress = 0.0
    @State private var isUploadComplete = false
    @State private var errorMessage: String?
    @FocusState private var isContentFocused: Bool
    private let service = PhotoService.shared

    init(
        trip: TripDetail,
        dayNumber: Int,
        embedded: Bool = false,
        onShowAll: @escaping () -> Void = {},
        onUploaded: @escaping () -> Void
    ) {
        self.trip = trip
        self.dayNumber = dayNumber
        self.embedded = embedded
        self.onShowAll = onShowAll
        self.onUploaded = onUploaded
    }

    var body: some View {
        Group {
            if embedded {
                photoFormContent
            } else {
                NavigationStack {
                    photoFormContent
                        .navigationTitle("\(dayNumber)일차 사진 기록")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
                }
            }
        }
        .alert("업로드 완료!", isPresented: $isUploadComplete) {
            Button("전체 사진 보기") {
                onUploaded()
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(180))
                    onShowAll()
                }
            }
            Button("나중에", role: .cancel) {
                onUploaded()
            }
        } message: {
            Text("사진이 모임에 공유되었어요.")
        }
        .confirmationDialog(
            "사진 추가",
            isPresented: $isPhotoSourcePresented,
            titleVisibility: .visible
        ) {
            Button {
                presentCamera()
            } label: {
                Label("카메라로 촬영", systemImage: "camera")
            }

            Button {
                isPhotoLibraryPresented = true
            } label: {
                Label("사진 보관함에서 선택", systemImage: "photo.on.rectangle")
            }

            Button("취소", role: .cancel) {}
        }
        .photosPicker(
            isPresented: $isPhotoLibraryPresented,
            selection: $selectedItem,
            matching: .images
        )
        .fullScreenCover(isPresented: $isCameraPresented) {
            CameraImagePicker(
                onImagePicked: { image in
                    loadCameraPhoto(image)
                    isCameraPresented = false
                },
                onCancel: {
                    isCameraPresented = false
                }
            )
            .ignoresSafeArea()
        }
    }

    private var photoFormContent: some View {
        ScrollView {
            VStack(spacing: 16) {
                    if let slot {
                        VStack(spacing: 4) {
                            Text("\(LocalDateTimeFormatter.time(from: slot.startTime)) ~ \(LocalDateTimeFormatter.time(from: slot.endTime))")
                                .font(.caption.bold())
                            Text(slot.confirmedPlaceName ?? "자유롭게 한 컷")
                                .font(.headline)
                        }
                        .foregroundStyle(.blue)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))
                    } else if !isLoading {
                        HStack(spacing: 12) {
                            Text("😊").font(.title2)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("자유 시간").font(.headline)
                                Text("자유 시간에도 사진을 남겨보세요!")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                        .padding(16)
                        .background(TripLogPalette.surfaceMuted, in: RoundedRectangle(cornerRadius: 18))
                    }

                    Button {
                        isPhotoSourcePresented = true
                    } label: {
                        Group {
                            if slot?.isTaken == true {
                                VStack(spacing: 10) {
                                    Image(systemName: "camera").font(.system(size: 42))
                                    Text("해당 타임라인에 이미 사진 찍으셨네요!")
                                        .font(.subheadline.bold())
                                    Text("전체 보기를 눌러 모임에서 찍은 사진을 구경하세요")
                                        .font(.caption)
                                }
                                .foregroundStyle(TripLogPalette.blue)
                            } else if let previewImage {
                                Image(uiImage: previewImage).resizable().scaledToFit()
                            } else {
                                VStack(spacing: 10) {
                                    Image(systemName: "camera").font(.system(size: 42))
                                    Text("탭해서 사진 추가")
                                    Text("HEIC·HEIF 형식은 현재 지원하지 않습니다.")
                                        .font(.caption)
                                }
                                .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 330)
                        .glassEffect(.regular.interactive(), in: RoundedRectangle(cornerRadius: 20))
                        .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .onChange(of: selectedItem) { _, newValue in
                        Task { await loadPhoto(newValue) }
                    }
                    .disabled(slot?.isTaken == true)

                    ZStack(alignment: .bottomTrailing) {
                        TextField("사진과 함께 남길 내용", text: $content, axis: .vertical)
                            .lineLimit(3...5)
                            .padding(.horizontal, 16)
                            .padding(.top, 14)
                            .padding(.bottom, 22)
                            .focused($isContentFocused)
                            .disabled(slot?.isTaken == true)
                            .onChange(of: content) { _, value in content = String(value.prefix(20)) }

                        Text("\(content.count)/20")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .padding(.trailing, 14)
                            .padding(.bottom, 8)
                            .allowsHitTesting(false)
                    }
                    .glassEffect(.regular.interactive(), in: RoundedRectangle(cornerRadius: 16))
                    .tripLogFocusRing(isFocused: isContentFocused, cornerRadius: 16)

                    if let errorMessage { Text(errorMessage).font(.footnote).foregroundStyle(.red) }

                    if isUploading {
                        ProgressView(value: uploadProgress, total: 1)
                            .progressViewStyle(.linear)
                            .tint(TripLogPalette.blue)
                            .padding(.horizontal, 4)
                            .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }

                    if !isUploading {
                        Button {
                            Task { await upload() }
                        } label: {
                            Text("사진 올리기")
                                .font(.body.bold())
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                        }
                        .buttonStyle(.glassProminent)
                        .tint(.green)
                        .disabled(imageData == nil || isLoading || slot?.isTaken == true)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }
            }
            .padding(TripLogDesign.horizontalPadding)
            .padding(.bottom, embedded ? 84 : 16)
            .animation(.easeInOut(duration: 0.2), value: isUploading)
        }
        .task { await loadSlot() }
    }

    private func loadSlot() async {
        isLoading = true
        do { slot = try await service.currentSlot(tripID: trip.id, dayNumber: dayNumber) }
        catch { errorMessage = error.localizedDescription }
        isLoading = false
    }

    private func loadPhoto(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        do {
            let data = try await item.loadTransferable(type: Data.self)
            imageData = data
            previewImage = data.flatMap(UIImage.init(data:))
            if let type = item.supportedContentTypes.first {
                mimeType = type.preferredMIMEType ?? "image/jpeg"
                filename = "photo.\(type.preferredFilenameExtension ?? "jpg")"
            }
        } catch { errorMessage = "선택한 사진을 읽지 못했습니다." }
    }

    private func presentCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            errorMessage = "이 기기에서는 카메라를 사용할 수 없습니다."
            return
        }
        isCameraPresented = true
    }

    private func loadCameraPhoto(_ image: UIImage) {
        guard let data = image.jpegData(compressionQuality: 0.95) else {
            errorMessage = "촬영한 사진을 읽지 못했습니다."
            return
        }

        selectedItem = nil
        imageData = data
        previewImage = image
        filename = "camera-photo.jpg"
        mimeType = "image/jpeg"
        errorMessage = nil
    }

    private func upload() async {
        guard let imageData else { return }
        uploadProgress = 0
        isUploading = true
        errorMessage = nil
        do {
            try await service.upload(
                tripID: trip.id,
                timelineID: slot?.timelineId,
                content: content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : content,
                imageData: imageData,
                filename: filename,
                mimeType: mimeType,
                onProgress: { progress in
                    withAnimation(.easeOut(duration: 0.12)) {
                        uploadProgress = progress
                    }
                }
            )
            resetUploadForm()
            if embedded {
                markCurrentSlotAsTaken()
                isUploadComplete = true
                Task { await loadSlot() }
            } else {
                onUploaded()
                dismiss()
            }
        } catch { errorMessage = error.localizedDescription }
        isUploading = false
        if errorMessage != nil {
            uploadProgress = 0
        }
    }

    private func resetUploadForm() {
        isContentFocused = false
        selectedItem = nil
        imageData = nil
        previewImage = nil
        filename = "photo.jpg"
        mimeType = "image/jpeg"
        content = ""
    }

    private func markCurrentSlotAsTaken() {
        guard let slot else { return }
        self.slot = CurrentPhotoSlot(
            startTime: slot.startTime,
            endTime: slot.endTime,
            timelineId: slot.timelineId,
            confirmedPlaceName: slot.confirmedPlaceName,
            isTaken: true
        )
    }
}

private struct CameraImagePicker: UIViewControllerRepresentable {
    let onImagePicked: (UIImage) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let parent: CameraImagePicker

        init(parent: CameraImagePicker) {
            self.parent = parent
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            guard let image = info[.originalImage] as? UIImage else {
                parent.onCancel()
                return
            }
            parent.onImagePicked(image)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCancel()
        }
    }
}
