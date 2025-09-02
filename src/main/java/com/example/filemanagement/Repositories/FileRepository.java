package com.example.filemanagement.Repositories;


import com.example.filemanagement.Models.FileModel;
import com.example.filemanagement.Models.FolderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRepository extends JpaRepository<FileModel,Long> {

    List<FileModel> findByFolderAndDeletedAtIsNull(FolderModel folder);

    List<FileModel> findByFolder(FolderModel folder);

    List<FileModel> findByDeletedAtIsNotNullAndFolderDeletedAtIsNull();
}
