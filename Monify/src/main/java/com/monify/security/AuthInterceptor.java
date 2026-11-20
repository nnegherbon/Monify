package com.monify.security;

import com.monify.controller.UserController;
import com.monify.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exige um token de sessão válido em todas as rotas /api (exceto login,
 * cadastro e leitura de categorias) e garante que o usuário autenticado só
 * acesse os próprios dados: qualquer {userId} na URL, ?userId= na query ou
 * {id} nas rotas de /api/users precisa ser o do usuário logado.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_USER_ID = "authUserId";

    private final AuthService authService;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/categories")) {
            return true;
        }

        Optional<Long> authUserId = authService.findUserIdByToken(extractToken(request));
        if (authUserId.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Sessão expirada. Faça login novamente.");
            return false;
        }

        Long userId = authUserId.get();
        request.setAttribute(AUTH_USER_ID, userId);

        for (String claimed : claimedUserIds(request, handler)) {
            if (!String.valueOf(userId).equals(claimed)) {
                writeError(response, HttpStatus.FORBIDDEN, "Acesso negado");
                return false;
            }
        }
        return true;
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }

    @SuppressWarnings("unchecked")
    private List<String> claimedUserIds(HttpServletRequest request, Object handler) {
        List<String> claimed = new ArrayList<>();
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> rawVars) {
            Map<String, String> vars = (Map<String, String>) rawVars;
            if (vars.containsKey("userId")) {
                claimed.add(vars.get("userId"));
            }
            if (handler instanceof HandlerMethod method
                    && UserController.class.isAssignableFrom(method.getBeanType())
                    && vars.containsKey("id")) {
                claimed.add(vars.get("id"));
            }
        }
        String queryUserId = request.getParameter("userId");
        if (queryUserId != null) {
            claimed.add(queryUserId);
        }
        return claimed;
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
