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
    private static final int BATCH_SIZE = 100;

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[EmbeddingConfig.DIMENSION] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allResults = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> sub = texts.subList(i, Math.min(texts.size(), i + BATCH_SIZE));
            allResults.addAll(callOpenAiEmbed(sub));
        }
        return allResults;
    }

    @SuppressWarnings("unchecked")
    private List<float[]> callOpenAiEmbed(List<String> texts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", "text-embedding-3-small",
                "input", texts
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = rest.postForEntity(
                "https://api.openai.com/v1/embeddings",
                entity,
                Map.class
        );

        Map<String, Object> respBody = resp.getBody();
        if (respBody == null || !respBody.containsKey("data")) {
            return List.of();
        }

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) respBody.get("data");
        dataList.sort(Comparator.comparingInt(item -> ((Number) item.get("index")).intValue()));

        int dim = EmbeddingConfig.DIMENSION;
        List<float[]> results = new ArrayList<>(dataList.size());

        for (Map<String, Object> item : dataList) {
            List<Double> vector = (List<Double>) item.get("embedding");
            float[] f = new float[dim];
            for (int i = 0; i < dim; i++) {
                f[i] = vector.get(i).floatValue();
            }
            results.add(f);
        }

        return results;
    }
}