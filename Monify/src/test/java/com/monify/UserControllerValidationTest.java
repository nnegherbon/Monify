package com.monify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerWithShortPasswordReturnsReadableMessage() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Teste\",\"email\":\"teste.validacao@example.com\",\"password\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Senha deve ter no mínimo 6 caracteres"))
                .andExpect(jsonPath("$.fields.password").value("Senha deve ter no mínimo 6 caracteres"));
    }

    @Test
    void registerWithInvalidEmailReturnsReadableMessage() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Teste\",\"email\":\"nao-e-email\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email deve ser válido"));
    }
}
