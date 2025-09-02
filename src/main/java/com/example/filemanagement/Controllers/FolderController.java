package com.example.filemanagement.Controllers;

import com.example.filemanagement.DTOs.ContentDto;
import com.example.filemanagement.DTOs.FolderDto;
import com.example.filemanagement.Service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;

@RestController
@RequestMapping("/api/folder")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService){
        this.folderService = folderService;
    }

    // 8. Create a Folder
    @PostMapping()
    public ResponseEntity<FolderDto> createFolder(@RequestBody FolderDto folderDto) {
        FolderDto createdFolder = folderService.createFolder(folderDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFolder);
    }

    // 9. Get Folder Contents
    @GetMapping("/{id}")
    public ResponseEntity<?> getFolderDetails(@PathVariable Long id) {
        return folderService.getFolderDetails(id);
    }

    // 9. Get Folder Contents
    @GetMapping("/{id}/content")
    public ResponseEntity<?> getFolderContents(@PathVariable Long id) {
        return folderService.getFolderContents(id);
    }

    //method to get content of the root folder.

    // 10. Download Folder as ZIP
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFolder(@PathVariable Long id) {
        return folderService.downloadFolder(id);
    }

    // 11. Rename or Move Folder
    @PatchMapping("/{id}")
    public ResponseEntity<FolderDto> renameOrMoveFolder(@PathVariable Long id,
                                                        @RequestBody FolderDto folderDto) {
        FolderDto updatedFolder = folderService.renameOrMoveFolder(id, folderDto);
        return ResponseEntity.ok(updatedFolder);
    }

    // 12. Soft Delete Folder (move to Bin)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable Long id,
                                          @RequestParam(value = "permanent", required = false) Boolean permanent) {
        if (Boolean.TRUE.equals(permanent)) {
            folderService.deleteFolderPermanently(id);
            return ResponseEntity.ok().body("{\"message\": \"Folder was permanently deleted.\"}");
        } else {
            folderService.moveFolderToBin(id);
            return ResponseEntity.ok().body("{\"message\": \"Folder moved to Bin\"}");
        }
    }

    // 13. Restore Folder from Bin
    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreFolder(@PathVariable Long id) {
        folderService.restoreFolder(id);
        return ResponseEntity.ok().body("{\"message\": \"Folder restored successfully\"}");
    }

    // 14. Get bin content
    @GetMapping("/bin")
    public ResponseEntity<?> binContent(){
        return folderService.getBinContent();
    }
}
