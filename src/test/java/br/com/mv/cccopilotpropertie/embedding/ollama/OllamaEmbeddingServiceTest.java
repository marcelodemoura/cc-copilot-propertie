package br.com.mv.cccopilotpropertie.embedding.ollama;

import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingServiceTest {

    @Mock
    private RestTemplate rest;

    @Test
    void shouldReturnVectorWithExactConfigDimension() {
        OllamaEmbeddingService service = new OllamaEmbeddingService("http://localhost:11434", "nomic-embed-text", rest);

        Map<String, Object> responseBody = Map.of(
                "data", List.of(
                        Map.of("embedding", List.of(0.25, 0.5, 0.75))
                )
        );

        when(rest.postForEntity(eq("http://localhost:11434/v1/embeddings"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        float[] result = service.embed("texto de teste");

        assertEquals(EmbeddingConfig.DIMENSION, result.length);
        assertEquals(0.25f, result[0]);
        assertEquals(0.5f, result[1]);
        assertEquals(0.75f, result[2]);
        assertEquals(0.0f, result[3]);
    }
}
