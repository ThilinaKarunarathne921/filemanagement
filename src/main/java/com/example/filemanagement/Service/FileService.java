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

        FileModel fileModel = fileRepository.findById(id) //Find file metadata
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));  //not found

        Path filePath = Paths.get(uploadDir).resolve(fileModel.getStorageKey()).normalize();  //Build path to file

        UrlResource resource = null;
        try {
            resource = new UrlResource(filePath.toUri());  // Load file as Resource
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("File can't get from the disk" + e);
        }

        return ResponseEntity.ok()   //Build response with headers
                .contentType(MediaType.parseMediaType(fileModel.getType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileModel.getName() + "\"")
                .body(resource);

    }

    public ResponseEntity<FileDto> renameOrMoveFile(Long id, FileDto fileDto) {

        FileModel fileModel = fileRepository.findById(id)  //Find the file by ID
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));  // not found

        if (fileDto.getName() != null && !fileDto.getName().isBlank()) {  //change name ( if not null or blank)
            fileModel.setName(fileDto.getName());
        }

        if (fileDto.getFolderId() != null) {  // if new folder id is not null
            FolderModel folder = folderRepository.findById(fileDto.getFolderId())  // set new folder
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found with id: " + fileDto.getFolderId())); // folder id provided but not found
            fileModel.setFolder(folder);
        }
        else{ // folder id is null ( move to root directory)
            fileModel.setFolder(null);
        }

        fileRepository.save(fileModel);

        FileDto updatedDto = fileHelper.mapToDto(fileModel);

        return ResponseEntity.ok(updatedDto);
    }

    public ResponseEntity<FileDto> moveFileToBin(Long id) {
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        fileModel.setDeletedAt(LocalDateTime.now());
        FileModel updated = fileRepository.save(fileModel);

        return ResponseEntity.ok(fileHelper.mapToDto(updated));
    }

    public ResponseEntity<Void> deleteFilePermanently(Long id) {
        FileModel fileModel = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));

        fileRepository.delete(fileModel);

        return ResponseEntity.noContent().build();
    }

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
