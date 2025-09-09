package com.example.filemanagement.Service;

import com.example.filemanagement.DTOs.ContentDto;
import com.example.filemanagement.DTOs.FileDto;
import com.example.filemanagement.DTOs.FolderDto;
import com.example.filemanagement.Helpers.FileHelper;
import com.example.filemanagement.Helpers.FolderHelper;
import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import com.example.filemanagement.Models.UserModel;
import com.example.filemanagement.Repositories.FileRepository;
import com.example.filemanagement.Repositories.FolderRepository;
import com.example.filemanagement.Repositories.UserRepository;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FolderHelper folderHelpers;
    private final FileHelper fileHelper;

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository, UserRepository userRepository, FolderHelper folderHelpers, FileHelper fileHelper, FileService fileService) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.folderHelpers = folderHelpers;
        this.fileHelper = fileHelper;
    }


    public ResponseEntity<?> createFolder(FolderDto folderDto) {
        Long parentId = folderDto.getParentFolderId();
        if(parentId!=null && !folderRepository.existsById(parentId)){ // has a parent Id and No folder with that id
            throw new IllegalArgumentException(
                    "Parent folder with id "+folderDto.getParentFolderId()+" not exist"
            );
        }

        if (folderRepository.existsByNameAndParentFolder_Id(folderDto.getName(), parentId)) {  // has a folder with same name under same parent folder
            throw new IllegalArgumentException(
                    "A folder with name '" + folderDto.getName() + "' already exists in this parent folder."
            );
        }

        FolderModel folder = folderHelpers.mapToModel(folderDto); // create a model

        FolderModel savedFolder = folderRepository.save(folder);  // save model

        FolderDto createdFolder = folderHelpers.mapToDto(savedFolder);  //create dto from saved model

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdFolder);
    }

    public ResponseEntity<?> importFromZip(MultipartFile zipFile, Long userId, Long parentFolderId) throws IOException {
        UserModel user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));    //check owner User validity

        FolderModel parentFolder = null;
        if (parentFolderId != null) {                                      //check weather the file is belonged to root or inside a folder
            parentFolder = folderRepository.findById(parentFolderId)       //if parent id was provided, check the validity
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
        }

        File tempDir = Files.createTempDirectory("zip_import_").toFile();       // create a temp folder to extract zip file
        try {
            try (ZipInputStream zipIn = new ZipInputStream(zipFile.getInputStream())) {  // Extract zip to temp directory
                folderHelpers.extractZip(zipIn, tempDir);
            }

            File[] contents = tempDir.listFiles();  // get all files + folders to array.
            if (contents != null) {  // if content not empty
                for (File item : contents) {    // for each content
                    if (item.isDirectory()) {   // if it is a directory
                        folderHelpers.processDirectory(item, parentFolder, user);
                    } else {                    // if it is a file
                        // Handle files in root
                        folderHelpers.processFile(item, parentFolder, user);
                    }
                }
            }
        } finally {  // remove the temp file that zip file was extracted into
            FileUtils.deleteDirectory(tempDir);
        }
        return ResponseEntity.ok("Folder uploaded successfully!");
    }

    public ResponseEntity<?> getFolderContents(Long folderId) {

        FolderModel folder = folderRepository.findById(folderId)  // Find folder
                .orElseThrow(() -> new IllegalArgumentException("Folder not found with id: " + folderId));

        List<FolderModel> subFolders = folderRepository.findByParentFolderAndDeletedAtIsNull(folder); // Get child folders (not deleted)

        List<FileModel> files = fileRepository.findByFolderAndDeletedAtIsNull(folder); //Get files inside folder (not deleted)

        List<FolderDto> folderDtos = subFolders.stream() // Map to DTOs
                .map(folderHelpers::mapToDto)
                .toList();

        List<FileDto> fileDtos = files.stream()
                .map(fileHelper::mapToDto)
                .toList();

        ContentDto contentDto = new ContentDto(); // create ContentDto
        contentDto.setFolders(folderDtos);
        contentDto.setFiles(fileDtos);

        return ResponseEntity.ok(contentDto);
    }

    public ResponseEntity<?> getFolderDetails(Long folderId) {

        FolderModel folder = folderRepository.findById(folderId)  // get record
                .orElseThrow(() -> new IllegalArgumentException("Folder not found with id: " + folderId));  // no record found

        FolderDto dto = folderHelpers.mapToDto(folder);
        return ResponseEntity.ok(dto);
    }

    public ResponseEntity<?> downloadFolder(Long folderId) {

        FolderModel rootFolder = folderRepository.findById(folderId) // Find root folder
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        Path tempZip;  //Create temporary zip file
        try {
            tempZip = Files.createTempFile("folder-", ".zip");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create temp file for ZIP", e);
        }

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {   // Build zip recursively
            folderHelpers.addFolderToZip(rootFolder, "", zos);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create ZIP file", e);
        }

        Resource resource;
        try {   // Return as Resource
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

        FolderModel folder = folderRepository.findById(id)  //Find the folder to update
                .orElseThrow(() -> new RuntimeException("Folder not found with id: " + id));  // no file found for the id

        if (folderDto.getName() != null && !folderDto.getName().isBlank()) {  // has provided a new name
            folder.setName(folderDto.getName()); // set new name
        }

        if (folderDto.getParentFolderId() != null) { // has a parent folder id
            if(folderDto.getParentFolderId().equals(folder.getParentFolder().getId())){  //
                throw new IllegalArgumentException("cannot move to same directory");
            }
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

    public ResponseEntity<?> moveFolderToBin(Long folderId) {
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        folderHelpers.softDeleteFolderRecursively(folder);
        return ResponseEntity.ok().body("{\"message\": \"Folder moved to Bin\"}");
    }

    public ResponseEntity<?> deleteFolderPermanently(Long folderId) {
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        folderHelpers.deleteFolderRecursively(folder);
        return ResponseEntity.ok().body("{\"message\": \"Folder was permanently deleted.\"}");
    }

    public ResponseEntity<?> restoreFolder(Long folderId) {
        FolderModel folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (folder.getDeletedAt() == null) {
            throw new RuntimeException("Folder is not deleted");
        }

        folderHelpers.restoreFolderRecursively(folder);
        return ResponseEntity.ok().body("{\"message\": \"Folder restored successfully\"}");
    }

    public ResponseEntity<?> getBinContent() {
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

    public ResponseEntity<?> rootContent() {
        ContentDto content = new ContentDto();

        List<FileModel> files = fileRepository.findByFolder(null);
        List<FolderModel> folders = folderRepository.findByParentFolder(null);

        List<FileDto> fileDtos = files.stream()
                .map(fileHelper::mapToDto)
                .toList();

        List<FolderDto> folderDtos = folders.stream()
                .map(folderHelpers::mapToDto)
                .toList();

        content.setFiles(fileDtos);
        content.setFolders(folderDtos);

        return ResponseEntity.ok(content);
    }
}
