package com.monify.controller;

import com.monify.dto.SpendingLimitDTO;
import com.monify.entity.SpendingLimit;
import com.monify.service.SpendingLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users/{userId}/limits")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SpendingLimitController {

    private final SpendingLimitService spendingLimitService;

    @GetMapping
    public ResponseEntity<?> getLimits(
            @PathVariable Long userId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) SpendingLimit.LimitType type) {
        try {
            return ResponseEntity.ok(spendingLimitService.getLimits(userId, month, type));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> saveLimit(@PathVariable Long userId, @Valid @RequestBody SpendingLimitDTO limitDTO) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(spendingLimitService.createLimit(userId, limitDTO));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLimit(
            @PathVariable Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SpendingLimitDTO limitDTO) {
        try {
            return ResponseEntity.ok(spendingLimitService.updateLimit(userId, id, limitDTO));
        } catch (RuntimeException e) {
            return error(e, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLimit(@PathVariable Long userId, @PathVariable Long id) {
        try {
            spendingLimitService.deleteLimit(userId, id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Limite excluido com sucesso");
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
