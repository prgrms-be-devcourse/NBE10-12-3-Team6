package csh.back.domain.trip.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PostImageService {

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${file.upload.base-url}")
    private String baseUrl;

//    public String saveImage(MultipartFile image) throws IOException {
//
//        if (image == null || image.isEmpty()) {
//            throw new IllegalArgumentException("업로드된 이미지가 없습니다.");
//        }
//
//        try {
//            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
//
//            if (!Files.exists(uploadPath)) {
//                Files.createDirectories(uploadPath);
//            }
//
//            String originalName = image.getOriginalFilename();
//            String extension = "";
//            if (originalName != null && originalName.contains(".")) {
//                extension = originalName.substring(originalName.lastIndexOf("."));
//            }
//
//            String savedName = UUID.randomUUID() + extension;
//            Path savePath = uploadPath.resolve(savedName);
//
//            Files.write(savePath, image.getBytes());
//
//            return baseUrl + "/uploadedimages/" + savedName;
//
//        } catch (IOException e) {
//            throw new RuntimeException("이미지 저장 실패", e);
//        }
//    }
}