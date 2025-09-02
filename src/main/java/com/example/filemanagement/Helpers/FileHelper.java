package com.example.filemanagement.Helpers;

import com.example.filemanagement.DTOs.FileDto;
import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import com.example.filemanagement.Models.UserModel;
import com.example.filemanagement.Repositories.FileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class FileHelper {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final FileRepository fileRepository;

    public FileHelper(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /** Generate unique storage key */
    public static String generateStorageKey(String originalFilename) {
        return UUID.randomUUID().toString() + getFileExtension(originalFilename);
    }

    /** Get file extension */
    public static String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return "";
    }

    /** Save file to disk (flat storage) and return storage key */
    public String saveFileToDisk(MultipartFile file) throws IOException {
        String storageKey = generateStorageKey(file.getOriginalFilename());
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(storageKey);
        file.transferTo(filePath.toFile());
        return storageKey;
    }

    /** Build FileModel */
    public FileModel buildFileModel(MultipartFile file, String storageKey, FolderModel folder, UserModel user) {
        return FileModel.builder()
                .name(file.getOriginalFilename())
                .storageKey(storageKey)
                .size(file.getSize())
                .type(file.getContentType())
                .deletedAt(null)
                .folder(folder)
                .uploadedBy(user)
                .build();
    }

    /** Map FileModel to FileDto */
    public FileDto mapToDto(FileModel fileModel) {
        FileDto dto = new FileDto();
        dto.setId(fileModel.getId());
        dto.setName(fileModel.getName());
        dto.setStorageKey(fileModel.getStorageKey());
        dto.setSize(fileModel.getSize());
        dto.setType(fileModel.getType());
        dto.setCreatedAt(fileModel.getCreatedAt());
        dto.setModifiedAt(fileModel.getModifiedAt());
        dto.setDeletedAt(fileModel.getDeletedAt());
        dto.setFolderId(fileModel.getFolder() != null ? fileModel.getFolder().getId() : null);
        dto.setUploadedBy(fileModel.getUploadedBy().getId());
        return dto;
    }

    /** Save FileModel to DB */
    public FileModel saveFileModel(FileModel fileModel) {
        return fileRepository.save(fileModel);
    }
}
