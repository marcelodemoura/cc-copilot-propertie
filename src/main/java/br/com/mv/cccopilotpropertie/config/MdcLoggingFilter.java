package br.com.mv.cccopilotpropertie.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String TENANT_KEY = "tenantId";
    public static final String KB_KEY = "knowledgeBase";
    public static final String REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = request.getParameter("tenantId");
        }
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        String kb = request.getHeader("X-Knowledge-Base");
        if (kb == null || kb.isBlank()) {
            kb = request.getParameter("knowledgeBase");
        }

        try {
            MDC.put(REQUEST_ID_KEY, requestId);
            MDC.put(TENANT_KEY, tenantId);
            if (kb != null && !kb.isBlank()) {
                MDC.put(KB_KEY, kb);
            }

            response.setHeader("X-Request-ID", requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
