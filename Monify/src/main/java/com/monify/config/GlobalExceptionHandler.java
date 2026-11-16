package com.monify.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converte erros de validação (@Valid) em respostas no mesmo formato usado
 * pelos controllers: {"error": "mensagem"}. Sem isso o Spring devolve apenas
 * "Bad Request" e o frontend não consegue mostrar o motivo ao usuário.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }

        String message = fields.isEmpty()
                ? "Dados inválidos"
                : fields.values().stream().collect(Collectors.joining("; "));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Corpo da requisição inválido");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
