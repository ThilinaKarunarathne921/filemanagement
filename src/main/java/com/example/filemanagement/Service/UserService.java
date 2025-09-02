package com.example.filemanagement.Service;

import com.example.filemanagement.DTOs.UserDto;
import com.example.filemanagement.Models.UserModel;
import com.example.filemanagement.Repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ResponseEntity<?> addUser(UserDto dto) {

        UserModel model = new UserModel();
        model.setEmail(dto.getEmail());
        model.setUsername(dto.getUserName());
        model.setPassword(dto.getPassword());

        userRepository.save(model);

        return ResponseEntity.ok(model);
    }
}
