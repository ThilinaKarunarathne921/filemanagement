package com.example.filemanagement.Helpers;

import com.example.filemanagement.DTOs.FolderDto;
import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import com.example.filemanagement.Models.UserModel;
import com.example.filemanagement.Repositories.FileRepository;
import com.example.filemanagement.Repositories.FolderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class FolderHelper {

    @Value("${file.upload-dir}") // e.g., "/uploads"
    private String uploadDir;

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;

    public FolderHelper(FolderRepository folderRepository, FileRepository fileRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    public FolderDto mapToDto(FolderModel model) {
        if (model == null) {
            return null;
        }

        FolderDto dto = new FolderDto();
        dto.setId(model.getId());
        dto.setName(model.getName());

        // Use IDs instead of full objects
        dto.setParentFolderId(model.getParentFolder() != null ? model.getParentFolder().getId() : null);
        dto.setCreatedBy(model.getCreatedBy() != null ? model.getCreatedBy().getId() : null);

        dto.setCreatedAt(model.getCreatedAt());
        dto.setModifiedAt(model.getModifiedAt());
        dto.setDeletedAt(model.getDeletedAt());

        return dto;
    }

    public FolderModel mapToModel(FolderDto dto) {
        if (dto == null) {
            return null;
        }

        FolderModel model = new FolderModel();
        model.setId(dto.getId());
        model.setName(dto.getName());

        if (dto.getParentFolderId() != null) {
            FolderModel parent = new FolderModel();
            parent.setId(dto.getParentFolderId());
            model.setParentFolder(parent);
        }

        if (dto.getCreatedBy() != null) {
            UserModel user = new UserModel();
            user.setId(dto.getCreatedBy());
            model.setCreatedBy(user);
        }

        model.setCreatedAt(dto.getCreatedAt());
        model.setModifiedAt(dto.getModifiedAt());
        model.setDeletedAt(dto.getDeletedAt());

        return model;
    }

    public void addFolderToZip(FolderModel folder, String parentPath, ZipOutputStream zos) throws IOException {
        String folderPath = parentPath + folder.getName() + "/";

        // Step 0: Add folder entry itself (even if empty)
        zos.putNextEntry(new ZipEntry(folderPath));
        zos.closeEntry();

        // Step 1: Add subfolders recursively
        List<FolderModel> subFolders = folderRepository.findByParentFolderAndDeletedAtIsNull(folder);
        for (FolderModel subFolder : subFolders) {
            addFolderToZip(subFolder, folderPath, zos);
        }

        // Step 2: Add files inside this folder
        List<FileModel> files = fileRepository.findByFolderAndDeletedAtIsNull(folder);
        for (FileModel file : files) {
            Path filePath = Paths.get(uploadDir).resolve(file.getStorageKey()).normalize();
            if (Files.exists(filePath)) {
                ZipEntry zipEntry = new ZipEntry(folderPath + file.getName());
                zos.putNextEntry(zipEntry);
                Files.copy(filePath, zos);
                zos.closeEntry();
            }
        }
    }


    public void softDeleteFolderRecursively(FolderModel folder) {
        LocalDateTime now = LocalDateTime.now();

        // mark this folder deleted
        folder.setDeletedAt(now);
        folderRepository.save(folder);

        // mark all files in this folder deleted
        List<FileModel> files = fileRepository.findByFolderAndDeletedAtIsNull(folder);
        for (FileModel file : files) {
            file.setDeletedAt(now);
            fileRepository.save(file);
        }

        // mark all subfolders deleted (recursive)
        List<FolderModel> subFolders = folderRepository.findByParentFolderAndDeletedAtIsNull(folder);
        for (FolderModel subFolder : subFolders) {
            softDeleteFolderRecursively(subFolder);
        }
    }


    public void deleteFolderRecursively(FolderModel folder) {
        // delete all files in this folder
        List<FileModel> files = fileRepository.findByFolder(folder);
        for (FileModel file : files) {
            fileRepository.delete(file);
        }

        // delete all subfolders (recursive)
        List<FolderModel> subFolders = folderRepository.findByParentFolder(folder);
        for (FolderModel subFolder : subFolders) {
            deleteFolderRecursively(subFolder);
        }

        // delete this folder itself
        folderRepository.delete(folder);
    }

    public void restoreFolderRecursively(FolderModel folder) {
        // restore this folder
        folder.setDeletedAt(null);
        folderRepository.save(folder);

        // restore all files inside this folder
        List<FileModel> files = fileRepository.findByFolder(folder);
        for (FileModel file : files) {
            if (file.getDeletedAt() != null) {
                file.setDeletedAt(null);
                fileRepository.save(file);
            }
        }

        // restore all subfolders (recursive)
        List<FolderModel> subFolders = folderRepository.findByParentFolder(folder);
        for (FolderModel subFolder : subFolders) {
            if (subFolder.getDeletedAt() != null) {
                restoreFolderRecursively(subFolder);
            }
        }
    }

}
