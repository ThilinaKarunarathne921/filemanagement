package com.example.filemanagement.Repositories;

import com.example.filemanagement.Models.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserModel,Long> {

}
