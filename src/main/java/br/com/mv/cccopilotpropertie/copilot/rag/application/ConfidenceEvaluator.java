package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;

import java.util.List;

public interface ConfidenceEvaluator {

    double score(String question, String answer, List<SearchResult> context);

}
