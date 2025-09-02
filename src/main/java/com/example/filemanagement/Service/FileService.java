package com.example.filemanagement.Service;

import com.example.filemanagement.DTOs.FileDto;
import com.example.filemanagement.Helpers.FileHelper;
import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import com.example.filemanagement.Models.UserModel;
import com.example.filemanagement.Repositories.FileRepository;
import com.example.filemanagement.Repositories.FolderRepository;
import com.example.filemanagement.Repositories.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    @Value("${file.upload-dir}") // e.g., "/uploads"
    private String uploadDir;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final FileHelper fileHelper;

    public FileService(FileRepository fileRepository,
                       FolderRepository folderRepository,
                       UserRepository userRepository, FileHelper fileHelper) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.fileHelper = fileHelper;
    }

    public ResponseEntity<?> uploadFiles(List<MultipartFile> files, Long folderId, Long userId) {
        List<FileDto> uploadedFiles = new ArrayList<>();

        FolderModel folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        }

        UserModel user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        for (MultipartFile file : files) {
            try {
                String storageKey = fileHelper.saveFileToDisk(file);
                FileModel fileModel = fileHelper.buildFileModel(file, storageKey, folder, user);
                fileModel = fileHelper.saveFileModel(fileModel);
                uploadedFiles.add(fileHelper.mapToDto(fileModel));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to store file " + file.getOriginalFilename(), e);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(uploadedFiles);
    }


    public ResponseEntity<FileDto> getFileMetadata(Long id) {
        FileModel file = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        FileDto dto = fileHelper.mapToDto(file);
        return ResponseEntity.ok(dto);
    }


    public ResponseEntity<Resource> downloadFile(Long id) {

        // Step 1: Find file metadata
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        // Step 2: Build path to file
        Path filePath = Paths.get(uploadDir).resolve(fileModel.getStorageKey()).normalize();

        // Step 3: Load file as Resource
        UrlResource resource = null;
        try {
            resource = new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }

        if (resource ==null) {
            throw new IllegalArgumentException("File not found on disk: " + filePath);
        }

        // Step 4: Build response with headers
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileModel.getType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileModel.getName() + "\"")
                .body(resource);

    }


    public ResponseEntity<FileDto> renameOrMoveFile(Long id, FileDto fileDto) {
        // Step 1: Find the file by ID
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        // Step 2: Apply changes (only update what is provided in DTO)
        if (fileDto.getName() != null && !fileDto.getName().isBlank()) {
            fileModel.setName(fileDto.getName());
        }

        if (fileDto.getFolderId() != null) {
            FolderModel folder = folderRepository.findById(fileDto.getFolderId())
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found with id: " + fileDto.getFolderId()));
            fileModel.setFolder(folder);
        }

        // Step 3: Save changes
        fileRepository.save(fileModel);

        // Step 4: Map back to DTO
        FileDto updatedDto = fileHelper.mapToDto(fileModel);

        return ResponseEntity.ok(updatedDto);
    }

    // Move file to recycle bin (soft delete)
    public ResponseEntity<FileDto> moveFileToBin(Long id) {
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        fileModel.setDeletedAt(LocalDateTime.now());
        FileModel updated = fileRepository.save(fileModel);

        return ResponseEntity.ok(fileHelper.mapToDto(updated));
    }

    // Delete permanently (hard delete)
    public ResponseEntity<Void> deleteFilePermanently(Long id) {
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        fileRepository.delete(fileModel);

        return ResponseEntity.noContent().build();
    }

    // Restore file (undo soft delete)
    public ResponseEntity<FileDto> restoreFile(Long id) {
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        if (fileModel.getDeletedAt() == null) {
            throw new IllegalArgumentException("File is not in recycle bin");
        }

        fileModel.setDeletedAt(null);
        FileModel restored = fileRepository.save(fileModel);

        return ResponseEntity.ok(fileHelper.mapToDto(restored));
    }


}
