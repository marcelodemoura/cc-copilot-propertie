package br.com.mv.cccopilotpropertie;

import br.com.mv.cccopilotpropertie.llm.OpenAiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "llm.openai.api-key=dummy-test-key",
        "llm.openai.model=gpt-5-mini"
})
@ActiveProfiles("openai")
class OpenAiProfileContextTest {

    @Autowired(required = false)
    private OpenAiChatService openAiChatService;

    @Test
    void shouldInstantiateOpenAiChatServiceBean() {
        assertNotNull(openAiChatService, "OpenAiChatService deve ser instanciado corretamente pelo Spring sob o profile 'openai'");
    }
}
