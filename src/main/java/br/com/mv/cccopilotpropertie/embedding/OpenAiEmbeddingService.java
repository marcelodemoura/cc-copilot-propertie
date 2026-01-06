package br.com.mv.cccopilotpropertie.embedding;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class OpenAiEmbeddingService implements EmbeddingService {

    private final RestTemplate rest = new RestTemplate();
    private final String apiKey = System.getenv("OPENAI_API_KEY");

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

        List<Double> vector = (List<Double>) ((Map) ((List) resp.getBody().get("data")).get(0)).get("embedding");

        float[] f = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) f[i] = vector.get(i).floatValue();
        return f;
    }
}
