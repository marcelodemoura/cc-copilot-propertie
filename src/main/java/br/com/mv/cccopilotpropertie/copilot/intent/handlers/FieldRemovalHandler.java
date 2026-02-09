package br.com.mv.cccopilotpropertie.copilot.intent.handlers;

import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeTarget;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeType;
import br.com.mv.cccopilotpropertie.copilot.answer.AnswerBuilder;
import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Order(0)
@Service
public class FieldRemovalHandler implements CopilotIntentHandler {

    private final AnswerBuilder answerBuilder;


    private static final Pattern FIELD_PATTERN =
            Pattern.compile("campo\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    private final SearchRepository searchRepository;

    public FieldRemovalHandler(AnswerBuilder answerBuilder, SearchRepository searchRepository) {
        this.answerBuilder = answerBuilder;
        this.searchRepository = searchRepository;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.FIELD_REMOVAL;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        String q = question.toLowerCase();
        return q.contains("remover") && q.contains("campo");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        String field = extractField(question);
        if (field == null) {
            return simple("Não consegui identificar o campo.", 0.4);
        }

        List<SearchResult> usages =
                searchRepository.findUsagesByClassName(
                        tenantId,
                        knowledgeBase,
                        field
                );

        String dto = inferDto(usages);

        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD,
                ChangeType.REMOVE,
                field,
                dto,
                null,
                null
        );

        state.setField(field);
        state.setLastChange(change);
        state.setDto(dto);

        if (usages.isEmpty()) {
            return simple(
                    "O campo `" + field + "` **não possui usos**. Remoção segura.",
                    1.0
            );
        }

        return new CopilotAnswer(
                "O campo `" + field + "` é utilizado em **"
                        + usages.size()
                        + " locais**.\n\n"
                        + "👉 Próximo passo: *isso é breaking?*",
                toSources(usages),
                1.0,
                null,
                null
        );
    }

    private String extractField(String q) {
        Matcher m = FIELD_PATTERN.matcher(q);
        return m.find() ? m.group(1) : null;
    }

    private String inferDto(List<SearchResult> docs) {
        return docs.stream()
                .map(SearchResult::path)
                .filter(p -> p.endsWith("DTO.java"))
                .map(p -> p.substring(p.lastIndexOf("/") + 1).replace(".java", ""))
                .findFirst()
                .orElse(null);
    }

    private CopilotAnswer simple(String msg, double c) {
        return new CopilotAnswer(msg, List.of(), c, null, null);
    }

    private List<CopilotAnswer.Source> toSources(List<SearchResult> docs) {
        return docs.stream()
                .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                .toList();
    }
}
