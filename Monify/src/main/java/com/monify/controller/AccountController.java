package com.monify.controller;

import com.monify.dto.AccountDTO;
import com.monify.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users/{userId}/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<?> getAccounts(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(accountService.getAccountsByUserId(userId));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@PathVariable Long userId, @Valid @RequestBody AccountDTO accountDTO) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(userId, accountDTO));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long userId, @PathVariable Long id) {
        try {
            accountService.deleteAccount(userId, id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Conta excluida com sucesso");
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
