package br.com.mv.cccopilotpropertie.copilot.api;

import br.com.mv.cccopilotpropertie.copilot.application.CopilotService;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class CopilotControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CopilotService copilotService;

    @Test
    void shouldRejectUnauthorizedWhenApiKeyIsMissing() throws Exception {
        AskRequest request = new AskRequest("tenant-a", "kb-core", "Qual a regra?", "session-123");

        mockMvc.perform(post("/copilot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Informe uma X-API-Key válida."));
    }

    @Test
    void shouldAnswerQuestionSuccessfully() throws Exception {
        CopilotAnswer answer = new CopilotAnswer("Resposta gerada", List.of(), 0.98, null, null, null, null);
        when(copilotService.ask(eq("tenant-a"), eq("kb-core"), eq("Qual a regra de negocio?"), eq("session-123")))
                .thenReturn(answer);

        AskRequest request = new AskRequest("tenant-a", "kb-core", "Qual a regra de negocio?", "session-123");

        mockMvc.perform(post("/copilot/ask")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Resposta gerada"))
                .andExpect(jsonPath("$.confidence").value(0.98));
    }

    @Test
    void shouldReturnBadRequestWithUniformEnvelopeWhenValidationFails() throws Exception {
        // Envia pergunta em branco
        AskRequest invalid = new AskRequest("", "kb-core", "", null);

        mockMvc.perform(post("/copilot/ask")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.details").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldModifyFileViaEndpoint() throws Exception {
        when(copilotService.modifyFile(eq("tenant-a"), eq("kb-core"), eq("UsuarioDTO.java"), eq("old"), eq("new"), eq("test")))
                .thenReturn(br.com.mv.cccopilotpropertie.copilot.code.FileModificationResult.success(
                        "UsuarioDTO.java", "--- diff", "UsuarioDTO.java.bak", "Modificado com sucesso"
                ));

        ModifyFileRequest request = new ModifyFileRequest(
                "tenant-a", "kb-core", "UsuarioDTO.java", "old", "new", "test"
        );

        mockMvc.perform(post("/copilot/modify-file")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("MODIFIED"))
                .andExpect(jsonPath("$.path").value("UsuarioDTO.java"));
    }
}
