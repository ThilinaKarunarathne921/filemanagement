package com.example.filemanagement.Models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "folders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // For nested folders
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private FolderModel parentFolder;

    // The user who created the folder
    @ManyToOne
    @JoinColumn(name = "created_by")
    private UserModel createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    @Column(nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.modifiedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.modifiedAt = LocalDateTime.now();
    }

}
