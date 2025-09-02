package com.example.filemanagement.Controllers;

import com.example.filemanagement.DTOs.FileDto;
import com.example.filemanagement.Service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }


    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") List<MultipartFile> files,
                                         @RequestParam(value = "folderId", required = false) Long folderId,
                                         @RequestParam("userId") Long userId) {

            return fileService.uploadFiles(files, folderId, userId);

    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDto> getFileMetadata(@PathVariable Long id) {
        return fileService.getFileMetadata(id);
    }


    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadFile(@PathVariable Long id) {
        return fileService.downloadFile(id);
    }


    @PatchMapping("/{id}")
    public ResponseEntity<FileDto> renameOrMoveFile(@PathVariable Long id,
                                                    @RequestBody FileDto fileDto) {
        return fileService.renameOrMoveFile(id, fileDto);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> moveFileToBin(@PathVariable Long id,
                                           @RequestParam(value = "permanent", required = false) Boolean permanent) {
        if (Boolean.TRUE.equals(permanent)) {
            return fileService.deleteFilePermanently(id);
        } else {
            return fileService.moveFileToBin(id);
        }
    }


    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreFile(@PathVariable Long id) {
        return fileService.restoreFile(id);
    }


}
