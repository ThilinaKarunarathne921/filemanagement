package com.example.filemanagement.DTOs;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderDto {
    private Long id;
    private String name;

    private Long parentFolderId;   // Instead of full FolderModel
    private Long createdBy;        // Instead of full UserModel

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime deletedAt;
}

