package com.example.filemanagement.Repositories;

import com.example.filemanagement.Models.FolderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderModel,Long> {
    boolean existsByNameAndParentFolder_Id(String name, Long parentId);

    List<FolderModel> findByParentFolderAndDeletedAtIsNull(FolderModel folder);

    boolean existsByParentFolderAndNameAndDeletedAtIsNull(FolderModel newParent, String name);

    List<FolderModel> findByParentFolder(FolderModel folder);

    List<FolderModel> findByDeletedAtIsNotNullAndParentFolderDeletedAtIsNull();

    Optional<FolderModel> findByNameAndParentFolder(String name, FolderModel parentFolder);
}
