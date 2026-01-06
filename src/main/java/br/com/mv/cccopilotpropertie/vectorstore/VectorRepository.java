package br.com.mv.cccopilotpropertie.vectorstore;

import br.com.mv.cccopilotpropertie.domain.CodeChunk;

import java.util.List;

public interface VectorRepository {

    void save(CodeChunk chunk);

    List<String> search(float[] vector);
}
