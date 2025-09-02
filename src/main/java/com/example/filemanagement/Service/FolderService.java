package com.example.filemanagement.Service;

import com.example.filemanagement.DTOs.ContentDto;
import com.example.filemanagement.DTOs.FileDto;
import com.example.filemanagement.DTOs.FolderDto;
import com.example.filemanagement.Helpers.FileHelper;
import com.example.filemanagement.Helpers.FolderHelper;
import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import com.example.filemanagement.Repositories.FileRepository;
import com.example.filemanagement.Repositories.FolderRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipOutputStream;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final FolderHelper folderHelpers;
    private final FileHelper fileHelper;

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository, FolderHelper folderHelpers, FileHelper fileHelper) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.folderHelpers = folderHelpers;
        this.fileHelper = fileHelper;
    }


    public FolderDto createFolder(FolderDto folderDto) {
        Long parentId = folderDto.getParentFolderId();
        if(!folderRepository.existsById(folderDto.getParentFolderId())){
            throw new IllegalArgumentException(
                    "Parent folder with id "+folderDto.getParentFolderId()+" not exist"
            );
        }

        if (folderRepository.existsByNameAndParentFolder_Id(folderDto.getName(), parentId)) {
            throw new IllegalArgumentException(
                    "A folder with name '" + folderDto.getName() + "' already exists in this parent folder."
            );
        }

        // Convert DTO -> Entity
        FolderModel folder = folderHelpers.mapToModel(folderDto);

        // Save entity in DB
        FolderModel savedFolder = folderRepository.save(folder);

        // Convert back Entity -> DTO
        return folderHelpers.mapToDto(savedFolder);
    }

    public ResponseEntity<?> getFolderContents(Long folderId) {
        // Step 1: Find folder
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found with id: " + folderId));

        // Step 2: Get child folders (not deleted)
        List<FolderModel> subFolders = folderRepository.findByParentFolderAndDeletedAtIsNull(folder);

        // Step 3: Get files inside folder (not deleted)
        List<FileModel> files = fileRepository.findByFolderAndDeletedAtIsNull(folder);

        // Step 4: Map to DTOs
        List<FolderDto> folderDtos = subFolders.stream()
                .map(folderHelpers::mapToDto)
                .toList();

        List<FileDto> fileDtos = files.stream()
                .map(fileHelper::mapToDto)
                .toList();

        // Step 5: Wrap into ContentDto
        ContentDto contentDto = new ContentDto();
        contentDto.setFolders(folderDtos);
        contentDto.setFiles(fileDtos);

        return ResponseEntity.ok(contentDto);
    }

    public ResponseEntity<?> getFolderDetails(Long folderId) {

        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found with id: " + folderId));

        FolderDto dto = folderHelpers.mapToDto(folder);
        return ResponseEntity.ok(dto);
    }

    public ResponseEntity<Resource> downloadFolder(Long folderId) {
        // Step 1: Find root folder
        FolderModel rootFolder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        // Step 2: Create temporary zip file
        Path tempZip;
        try {
            tempZip = Files.createTempFile("folder-", ".zip");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create temp file for ZIP", e);
        }

        // Step 3: Build zip recursively
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
            folderHelpers.addFolderToZip(rootFolder, "", zos);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create ZIP file", e);
        }
        // Step 4: Return as Resource
        Resource resource;
        try {
            resource = new UrlResource(tempZip.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rootFolder.getName() + ".zip\"")
                .body(resource);
    }

    public FolderDto renameOrMoveFolder(Long id, FolderDto folderDto) {
        // Step 1: Find the folder to update
        FolderModel folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found with id: " + id));

        // Step 2: Handle rename
        if (folderDto.getName() != null && !folderDto.getName().isBlank()) {
            folder.setName(folderDto.getName());
        }

        // Step 3: Handle move
        if (folderDto.getParentFolderId() != null) {
            FolderModel newParent = folderRepository.findById(folderDto.getParentFolderId())
                    .orElseThrow(() -> new RuntimeException("Parent folder not found"));

            // Validate no folder with the same name exists in new parent
            boolean exists = folderRepository.existsByParentFolderAndNameAndDeletedAtIsNull(newParent, folder.getName());
            if (exists) {
                throw new RuntimeException("A folder with the same name already exists in the target folder");
            }

            folder.setParentFolder(newParent);
        }

        // Optional: update modifiedAt
        folder.setModifiedAt(LocalDateTime.now());

        // Step 4: Save updated folder
        FolderModel updatedFolder = folderRepository.save(folder);

        // Step 5: Map to DTO
        return folderHelpers.mapToDto(updatedFolder);
    }

    //soft delete
    public void moveFolderToBin(Long folderId) {
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        folderHelpers.softDeleteFolderRecursively(folder);
    }


    // Permanent delete (remove folder + all contents from DB)
    public void deleteFolderPermanently(Long folderId) {
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        folderHelpers.deleteFolderRecursively(folder);
    }

    public void restoreFolder(Long folderId) {
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (folder.getDeletedAt() == null) {
            throw new RuntimeException("Folder is not deleted");
        }

        folderHelpers.restoreFolderRecursively(folder);
    }


    public ResponseEntity<ContentDto> getBinContent() {
        // Find folders that are deleted but parent is not deleted
        List<FolderModel> deletedFolders = folderRepository.findByDeletedAtIsNotNullAndParentFolderDeletedAtIsNull();
        List<FolderDto> folderDtos = deletedFolders.stream()
                .map(folderHelpers::mapToDto)
                .toList();

        // Find files that are deleted but their folder is not deleted
        List<FileModel> deletedFiles = fileRepository.findByDeletedAtIsNotNullAndFolderDeletedAtIsNull();
        List<FileDto> fileDtos = deletedFiles.stream()
                .map(fileHelper::mapToDto)
                .toList();

        ContentDto binContent = new ContentDto();
        binContent.setFolders(folderDtos);
        binContent.setFiles(fileDtos);

        return ResponseEntity.ok(binContent);
    }


}
