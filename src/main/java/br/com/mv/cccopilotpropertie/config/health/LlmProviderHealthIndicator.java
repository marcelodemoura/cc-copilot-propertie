package br.com.mv.cccopilotpropertie.config.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class LlmProviderHealthIndicator implements HealthIndicator {

    private final Environment env;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String ollamaBaseUrl;
    private final String ollamaModel;

    public LlmProviderHealthIndicator(
            Environment env,
            @Value("${llm.openai.api-key:}") String openAiApiKey,
            @Value("${llm.openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${llm.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${llm.ollama.model:qwen2.5-coder}") String ollamaModel
    ) {
        this.env = env;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
    }

    @Override
    public Health health() {
        String[] profiles = env.getActiveProfiles();
        boolean isOpenAi = Arrays.asList(profiles).contains("openai");
        boolean isOllama = Arrays.asList(profiles).contains("ollama");

        if (isOpenAi) {
            if (openAiApiKey == null || openAiApiKey.isBlank()) {
                return Health.down()
                        .withDetail("provider", "openai")
                        .withDetail("status", "missing_api_key")
                        .withDetail("error", "Chave OPENAI_API_KEY não configurada")
                        .build();
            }
            return Health.up()
                    .withDetail("provider", "openai")
                    .withDetail("model", openAiModel)
                    .build();
        }

        if (isOllama) {
            return Health.up()
                    .withDetail("provider", "ollama")
                    .withDetail("baseUrl", ollamaBaseUrl)
                    .withDetail("model", ollamaModel)
                    .build();
        }

        return Health.up()
                .withDetail("provider", "mock")
                .withDetail("mode", "local-development")
                .build();
    }
}
