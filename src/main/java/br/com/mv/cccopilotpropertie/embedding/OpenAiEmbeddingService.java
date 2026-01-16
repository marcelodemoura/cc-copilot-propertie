package br.com.mv.cccopilotpropertie.embedding;

import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Profile("openai")
public class OpenAiEmbeddingService implements EmbeddingService {

    private final RestTemplate rest = new RestTemplate();
    private final String apiKey;

    public OpenAiEmbeddingService(
            @Value("${llm.openai.api-key}") String apiKey
    ) {
        this.apiKey = apiKey;
    }
    @Override
    public float[] embed(String text) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", "text-embedding-3-small",
                "input", text
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = rest.postForEntity(
                "https://api.openai.com/v1/embeddings",
                entity,
                Map.class
        );

        List<Double> vector =
                (List<Double>) ((Map) ((List) resp.getBody().get("data")).get(0))
                        .get("embedding");

        int dim = EmbeddingConfig.DIMENSION;
        float[] f = new float[dim];

        for (int i = 0; i < dim; i++) {
            f[i] = vector.get(i).floatValue();
        }

        return f;
    }

}