package com.example.filemanagement.Controllers;

import com.example.filemanagement.DTOs.ContentDto;
import com.example.filemanagement.DTOs.FolderDto;
import com.example.filemanagement.Service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService){
        this.folderService = folderService;
    }

    @PostMapping()
    public ResponseEntity<?> createFolder(@RequestBody FolderDto folderDto) {
        return folderService.createFolder(folderDto);
    }

    @PostMapping("/upload-zip")
    public ResponseEntity<?> uploadZip(@RequestParam("file") MultipartFile file,
                                            @RequestParam("userId") Long userId,
                                            @RequestParam(value = "parentFolderId", required = false) Long parentFolderId) {
        try {
            return folderService.importFromZip(file, userId, parentFolderId);
        } catch (IOException e) {
            throw new IllegalArgumentException("File upload failed..."+e.getLocalizedMessage());
        }

    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFolderDetails(@PathVariable Long id) {
        return folderService.getFolderDetails(id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<?> getFolderContents(@PathVariable Long id) {
        return folderService.getFolderContents(id);
    }

    //method to get content of the root folder.

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadFolder(@PathVariable Long id) {
        return folderService.downloadFolder(id);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FolderDto> renameOrMoveFolder(@PathVariable Long id,
                                                        @RequestBody FolderDto folderDto) {
        FolderDto updatedFolder = folderService.renameOrMoveFolder(id, folderDto);
        return ResponseEntity.ok(updatedFolder);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable Long id,
                                          @RequestParam(value = "permanent", required = false) Boolean permanent) {
        if (Boolean.TRUE.equals(permanent)) {
            return folderService.deleteFolderPermanently(id);
        } else {
            return folderService.moveFolderToBin(id);
        }
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreFolder(@PathVariable Long id) {
        return folderService.restoreFolder(id);
    }

    @GetMapping("/bin")
    public ResponseEntity<?> binContent(){
        return folderService.getBinContent();
    }
}
