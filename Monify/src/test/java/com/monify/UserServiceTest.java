package com.monify;

import com.monify.dto.UserDTO;
import com.monify.entity.User;
import com.monify.repository.UserRepository;
import com.monify.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
    }

    @Test
    public void testRegisterUserSuccess() {
        UserDTO userDTO = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        User registered = userService.registerUser(userDTO);

        assertNotNull(registered.getId());
        assertEquals("John Doe", registered.getName());
        assertEquals("john@example.com", registered.getEmail());
    }

    @Test
    public void testRegisterUserDuplicateEmail() {
        UserDTO userDTO = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        userService.registerUser(userDTO);

        assertThrows(RuntimeException.class, () -> userService.registerUser(userDTO));
    }

    @Test
    public void testAuthenticateUserSuccess() {
        UserDTO userDTO = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();
        userService.registerUser(userDTO);

        User authenticated = userService.authenticateUser("john@example.com", "password123");

        assertNotNull(authenticated);
        assertEquals("John Doe", authenticated.getName());
    }

    @Test
    public void testAuthenticateUserWrongPassword() {
        UserDTO userDTO = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();
        userService.registerUser(userDTO);

        assertThrows(RuntimeException.class, () -> userService.authenticateUser("john@example.com", "wrongpassword"));
    }

    @Test
    public void testUpdateUser() {
        UserDTO userDTO = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();
        User user = userService.registerUser(userDTO);

        UserDTO updateDTO = UserDTO.builder()
                .name("John Updated")
                .build();

        User updated = userService.updateUser(user.getId(), updateDTO);

        assertEquals("John Updated", updated.getName());
    }

    @Test
    public void testDeleteUser() {
        UserDTO userDTO = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();
        User user = userService.registerUser(userDTO);
        Long userId = user.getId();

        userService.deleteUser(userId);

        assertThrows(RuntimeException.class, () -> userService.getUserById(userId));
    }
}
