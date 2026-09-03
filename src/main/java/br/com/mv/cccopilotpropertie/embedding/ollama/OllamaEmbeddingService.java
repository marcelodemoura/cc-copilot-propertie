package br.com.mv.cccopilotpropertie.embedding.ollama;

import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Profile("ollama")
public class OllamaEmbeddingService implements EmbeddingService {

    private final String baseUrl;
    private final String model;
    private final RestTemplate rest;

    public OllamaEmbeddingService(
            @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${llm.ollama.embedding-model:nomic-embed-text}") String model
    ) {
        this(baseUrl, model, new RestTemplate());
    }

    public OllamaEmbeddingService(String baseUrl, String model, RestTemplate rest) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.rest = rest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", text
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = rest.postForEntity(
                baseUrl + "/v1/embeddings",
                entity,
                Map.class
        );

        if (resp.getBody() == null || !resp.getBody().containsKey("data")) {
            return new float[EmbeddingConfig.DIMENSION];
        }

        List<?> dataList = (List<?>) resp.getBody().get("data");
        if (dataList == null || dataList.isEmpty()) {
            return new float[EmbeddingConfig.DIMENSION];
        }

        Map<?, ?> firstEntry = (Map<?, ?>) dataList.get(0);
        List<Double> vector = (List<Double>) firstEntry.get("embedding");
        if (vector == null) {
            return new float[EmbeddingConfig.DIMENSION];
        }

        int dim = EmbeddingConfig.DIMENSION;
        float[] f = new float[dim];

        for (int i = 0; i < Math.min(dim, vector.size()); i++) {
            f[i] = vector.get(i).floatValue();
        }

        return f;
    }
}
