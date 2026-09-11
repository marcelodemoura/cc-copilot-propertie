package br.com.mv.cccopilotpropertie.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MdcLoggingFilterTest {

    @Test
    void shouldPopulateMdcDuringRequestAndClearAfter() throws ServletException, IOException {
        MdcLoggingFilter filter = new MdcLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("X-Tenant-ID", "tenant-hospital-a");
        request.addHeader("X-Knowledge-Base", "kb-prescricao");
        request.addHeader("X-Request-ID", "req-12345");

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        AtomicReference<String> capturedKb = new AtomicReference<>();
        AtomicReference<String> capturedReqId = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedTenant.set(MDC.get(MdcLoggingFilter.TENANT_KEY));
            capturedKb.set(MDC.get(MdcLoggingFilter.KB_KEY));
            capturedReqId.set(MDC.get(MdcLoggingFilter.REQUEST_ID_KEY));
        };

        filter.doFilter(request, response, chain);

        assertEquals("tenant-hospital-a", capturedTenant.get());
        assertEquals("kb-prescricao", capturedKb.get());
        assertEquals("req-12345", capturedReqId.get());

        // Header de resposta deve conter o request ID
        assertEquals("req-12345", response.getHeader("X-Request-ID"));

        // MDC deve ter sido limpo no bloco finally
        assertNull(MDC.get(MdcLoggingFilter.TENANT_KEY));
        assertNull(MDC.get(MdcLoggingFilter.REQUEST_ID_KEY));
    }
}
