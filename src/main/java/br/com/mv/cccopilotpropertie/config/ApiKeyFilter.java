package br.com.mv.cccopilotpropertie.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String apiKey;
    private final boolean isLocalDevEnvironment;

    public ApiKeyFilter(@Value("${api.key:}") String apiKey, Environment env) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.isLocalDevEnvironment = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("mock") || p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("test"));
    }

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui", "/swagger-ui.html", "/v3/api-docs", "/webjars",
            "/index.html", "/static", "/favicon.ico", "/actuator"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Permite requisições preflight de CORS sem autenticação
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.equals("/")) {
            filterChain.doFilter(request, response);
            return;
        }

        for (String pub : PUBLIC_PATHS) {
            if (path.startsWith(pub)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Se a chave não estiver configurada no servidor:
        if (apiKey.isBlank()) {
            if (isLocalDevEnvironment) {
                // Em ambiente de teste/mock/dev puramente local, permite com aviso
                filterChain.doFilter(request, response);
                return;
            }
            // Em produção/fora de mock, rejeita todas as requisições se não houver chave definida
            rejectUnauthorized(response, "Configuração de segurança pendente: API_KEY não definida no servidor.");
            return;
        }

        String providedKey = request.getHeader("X-API-Key");
        if (providedKey != null && !providedKey.isBlank() && constantTimeEquals(apiKey, providedKey.trim())) {
            filterChain.doFilter(request, response);
            return;
        }

        rejectUnauthorized(response, "Informe uma X-API-Key válida.");
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
