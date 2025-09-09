package com.example.filemanagement.Helpers;

import com.example.filemanagement.DTOs.FolderDto;
import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import com.example.filemanagement.Models.UserModel;
import com.example.filemanagement.Repositories.FileRepository;
import com.example.filemanagement.Repositories.FolderRepository;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class FolderHelper {

    @Value("${file.upload-dir}") // e.g., "/uploads"
    private String uploadDir;

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final FileHelper fileHelper;

    public FolderHelper(FolderRepository folderRepository, FileRepository fileRepository, FileHelper fileHelper) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.fileHelper = fileHelper;
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

        zos.putNextEntry(new ZipEntry(folderPath)); //Add folder entry itself (even if empty)
        zos.closeEntry();

        List<FolderModel> subFolders = folderRepository.findByParentFolderAndDeletedAtIsNull(folder);  //Add subfolders recursively (not deleted)
        for (FolderModel subFolder : subFolders) {
            addFolderToZip(subFolder, folderPath, zos);
        }

        List<FileModel> files = fileRepository.findByFolderAndDeletedAtIsNull(folder);   //Add files inside this folder (not deleted)
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

    //Process directories found on extracted zip file
    public void processDirectory(File directory, FolderModel parentFolder, UserModel user) throws IOException {
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
    public void processFile(File file, FolderModel folder, UserModel user) throws IOException {
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

    public void extractZip(ZipInputStream zipIn, File destDir) throws IOException {
        ZipEntry entry; // one item
        byte[] buffer = new byte[4096];

        while ((entry = zipIn.getNextEntry()) != null) {  // check next entry ( file or folder ) exist then
            File newFile = new File(destDir, entry.getName());  // get name of the next entry ( path/name.type)

            if (!newFile.toPath().normalize().startsWith(destDir.toPath().normalize())) { // Ensure the file will be created inside destDir ( path should start with destDir)
                throw new IOException("Entry is outside of target directory: " + entry.getName());
            }

            if (entry.isDirectory()) {  // if entry ends with "/"
                if (!newFile.isDirectory() && !newFile.mkdirs()) {  // create new directory
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {  // entry is a file
                File parent = newFile.getParentFile();  // set parent as current directory
                if (!parent.isDirectory() && !parent.mkdirs()) {  // parent folder is Not existed
                    throw new IOException("Failed to create directory " + parent);
                }

                try (FileOutputStream fos = new FileOutputStream(newFile)) { // Write file contents
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
    public static class MockMultipartFile implements MultipartFile {
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

}
