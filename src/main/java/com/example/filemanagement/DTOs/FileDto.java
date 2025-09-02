package com.example.filemanagement.DTOs;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDto {
    private Long id;
    private String name;
    private String storageKey;
    private Long size;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime deletedAt;

    private Long folderId;
    private Long uploadedBy;
}
