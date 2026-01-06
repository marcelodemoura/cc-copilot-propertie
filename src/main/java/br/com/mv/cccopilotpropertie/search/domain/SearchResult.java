package br.com.mv.cccopilotpropertie.search.domain;

public record SearchResult( String path,
                            String content,
                            double score) {
}
