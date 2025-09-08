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
import org.apache.commons.io.IOUtils;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FolderHelper folderHelpers;
    private final FileHelper fileHelper;
    private final FileService fileService;

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository, UserRepository userRepository, FolderHelper folderHelpers, FileHelper fileHelper, FileService fileService) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.folderHelpers = folderHelpers;
        this.fileHelper = fileHelper;
        this.fileService = fileService;
    }


    public ResponseEntity<?> createFolder(FolderDto folderDto) {
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

        FolderModel folder = folderHelpers.mapToModel(folderDto);

        FolderModel savedFolder = folderRepository.save(folder);

        FolderDto createdFolder = folderHelpers.mapToDto(savedFolder);

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
                extractZip(zipIn, tempDir);
            }

            File[] contents = tempDir.listFiles();  // get all files + folders to array.
            if (contents != null) {  // if content not empty
                for (File item : contents) {    // for each content
                    if (item.isDirectory()) {   // if it is a directory
                        processDirectory(item, parentFolder, user);
                    } else {                    // if it is a file
                        // Handle files in root
                        processFile(item, parentFolder, user);
                    }
                }
            }
        } finally {  // remove the temp file that zip file was extracted into
            FileUtils.deleteDirectory(tempDir);
        }
        return ResponseEntity.ok("Folder uploaded successfully!");
    }

    //Process directories found on extracted zip file
    private void processDirectory(File directory, FolderModel parentFolder, UserModel user) throws IOException {
        FolderModel currentFolder = folderRepository.findByNameAndParentFolder(directory.getName(), parentFolder)  //check for folder already exist ( same name under same parentFolder )
                .orElseGet(() -> {                                      //if no existing folder found, create new Folder Record
                    FolderModel newFolder = FolderModel.builder()
                            .name(directory.getName())
                            .parentFolder(parentFolder)
                            .createdBy(user)
                            .build();
                    return folderRepository.save(newFolder);
                });

        File[] contents = directory.listFiles();  // get content ( files and folders) of the current directory
        if (contents != null) {  // if it has content
            for (File item : contents) {  // recursively handle them
                if (item.isDirectory()) {
                    processDirectory(item, currentFolder, user);
                } else {
                    processFile(item, currentFolder, user);
                }
            }
        }
    }

    //process files found in content array
    private void processFile(File file, FolderModel folder, UserModel user) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {  // get file content from input stram
            MultipartFile multipartFile = new MockMultipartFile(   // create new multipart file using input stream of the file
                    file.getName(),
                    file.getName(),
                    Files.probeContentType(file.toPath()),
                    input
            );

            String storageKey = fileHelper.saveFileToDisk(multipartFile);   // save file into disk and get storageKey
            FileModel fileModel = fileHelper.buildFileModel(multipartFile, storageKey, folder, user); // crate a file Model
            fileHelper.saveFileModel(fileModel);  // save the file model in the DB
        }
    }

    private void extractZip(ZipInputStream zipIn, File destDir) throws IOException {
        ZipEntry entry;
        byte[] buffer = new byte[1024];

        while ((entry = zipIn.getNextEntry()) != null) {
            File newFile = new File(destDir, entry.getName());

            // Ensure the file will be created inside destDir
            if (!newFile.toPath().normalize().startsWith(destDir.toPath().normalize())) {
                throw new IOException("Entry is outside of target directory: " + entry.getName());
            }

            if (entry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // Create parent directories if they don't exist
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                // Write file contents
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zipIn.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
            zipIn.closeEntry();
        }
    }

    // Helper class for converting File to MultipartFile
    private static class MockMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public MockMultipartFile(String name, String originalFilename, String contentType, FileInputStream contentStream)
                throws IOException {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = IOUtils.toByteArray(contentStream);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
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

    public ResponseEntity<?> downloadFolder(Long folderId) {
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
