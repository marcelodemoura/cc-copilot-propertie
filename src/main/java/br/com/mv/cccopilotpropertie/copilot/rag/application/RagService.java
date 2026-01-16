package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagService {

    private final SearchService search;
    private final SearchRepository searchRepository; // 🔴 NOVO
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;

    private static final Pattern EXTENDS_PATTERN =
            Pattern.compile("extends\\s+(\\w+)");

    public RagService(
            SearchService search,
            SearchRepository searchRepository, // 🔴 NOVO
            PromptAssembler promptAssembler,
            AnswerService answer
    ) {
        this.search = search;
        this.searchRepository = searchRepository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
    }

    public CopilotAnswer ask(
            String tenantId,
            String knowledgeBase,
            String question
    ) {
        var docs = search.search(tenantId, knowledgeBase, question, 12);

        double confidence = calculateConfidence(docs);

        if (docs.isEmpty() || confidence < 0.15) {
            return new CopilotAnswer(
                    "Não encontrei informações suficientes na base de conhecimento para responder essa pergunta.",
                    List.of(),
                    confidence
            );
        }

        // 🔴 NOVO: enriquecer contexto com herança
        var enrichedDocs = enrichWithInheritance(
                tenantId,
                knowledgeBase,
                docs
        );

        var prompt = promptAssembler.build(question, enrichedDocs);
        var response = answer.ask(prompt);

        var sources = enrichedDocs.stream()
                .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                .toList();

        return new CopilotAnswer(
                response,
                sources,
                confidence
        );
    }

    // =========================================================
    // 🔽 HERANÇA CONSCIENTE (NOVO)
    // =========================================================

    private List<SearchResult> enrichWithInheritance(
            String tenantId,
            String knowledgeBase,
            List<SearchResult> docs
    ) {
        if (docs.isEmpty()) {
            return docs;
        }

        SearchResult child = docs.get(0);

        Optional<String> parentClass =
                extractParentClass(child.content());

        if (parentClass.isEmpty()) {
            return docs;
        }

        Optional<SearchResult> parentOpt =
                searchRepository.findByClassName(
                        tenantId,
                        knowledgeBase,
                        parentClass.get()
                );

        if (parentOpt.isEmpty()) {
            return docs;
        }

        SearchResult parent = parentOpt.get();

        List<SearchResult> enriched = new ArrayList<>();
        enriched.add(parent);   // DTO pai primeiro
        enriched.addAll(docs);  // depois o filho e demais

        return enriched;
    }


    private Optional<String> extractParentClass(String javaCode) {
        Matcher m = EXTENDS_PATTERN.matcher(javaCode);
        if (m.find()) {
            return Optional.of(m.group(1));
        }
        return Optional.empty();
    }

    // =========================================================
    // 🔽 CONFIDENCE (INALTERADO)
    // =========================================================

    private double calculateConfidence(List<SearchResult> docs) {
        if (docs == null || docs.isEmpty()) {
            return 0.0;
        }

        return docs.stream()
                .limit(3)
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0.0);
    }
}