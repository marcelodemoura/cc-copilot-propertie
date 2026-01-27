package br.com.mv.cccopilotpropertie.copilot.index;

import org.springframework.stereotype.Service;

@Service
public class DefaultFieldIndexer implements FieldIndexer {

    private final FieldIndexRepository repository;

    public DefaultFieldIndexer(FieldIndexRepository repository) {
        this.repository = repository;
    }

    @Override
    public void indexFile(
            String tenantId,
            String knowledgeBase,
            String path,
            String content
    ) {

    }
}
