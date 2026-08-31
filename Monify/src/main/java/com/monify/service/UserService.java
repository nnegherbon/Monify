package com.monify.service;

import com.monify.dto.UserDTO;
import com.monify.dto.ProfileDTO;
import com.monify.entity.User;
import com.monify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public User registerUser(UserDTO userDTO) {
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email já cadastrado");
        }

        User user = User.builder()
                .email(userDTO.getEmail())
                .password(userDTO.getPassword()) // Em produção, usar BCrypt
                .name(userDTO.getName())
                .build();

        return userRepository.save(user);
    }

    public User authenticateUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!user.getPassword().equals(password)) { // Em produção, usar BCrypt
            throw new RuntimeException("Senha incorreta");
        }

        return user;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, UserDTO userDTO) {
        User user = getUserById(id);
        user.setName(userDTO.getName());
        return userRepository.save(user);
    }

    public User updateProfile(Long id, ProfileDTO profileDTO) {
        User user = getUserById(id);
        userRepository.findByEmail(profileDTO.getEmail())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("Email ja cadastrado");
                });

        user.setName(profileDTO.getName());
        user.setEmail(profileDTO.getEmail());
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
