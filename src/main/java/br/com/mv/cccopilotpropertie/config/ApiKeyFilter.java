package br.com.mv.cccopilotpropertie.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String apiKey;

    public ApiKeyFilter(@Value("${api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui", "/swagger-ui.html", "/v3/api-docs", "/webjars",
            "/index.html", "/static", "/favicon.ico"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
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
        if (apiKey.isBlank() || apiKey.equals(request.getHeader("X-API-Key"))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Informe uma X-API-Key válida.");
    }
}
