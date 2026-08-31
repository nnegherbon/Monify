package com.monify.controller;

import com.monify.dto.CreditCardDTO;
import com.monify.service.CreditCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users/{userId}/cards")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CreditCardController {

    private final CreditCardService creditCardService;

    @GetMapping
    public ResponseEntity<?> getCards(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(creditCardService.getCardsByUserId(userId));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createCard(@PathVariable Long userId, @Valid @RequestBody CreditCardDTO cardDTO) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(creditCardService.createCard(userId, cardDTO));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable Long userId, @PathVariable Long id) {
        try {
            creditCardService.deleteCard(userId, id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Cartao excluido com sucesso");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return error(e, HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<Map<String, String>> error(RuntimeException e, HttpStatus status) {
        Map<String, String> error = new HashMap<>();
        error.put("error", e.getMessage());
        return ResponseEntity.status(status).body(error);
    }
}
