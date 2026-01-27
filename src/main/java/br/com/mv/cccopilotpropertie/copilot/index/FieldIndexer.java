package br.com.mv.cccopilotpropertie.copilot.index;

import org.springframework.stereotype.Service;

@Service
public interface FieldIndexer {


    void indexFile(
            String tenantId,
            String knowledgeBase,
            String path,
            String content
    );
}
