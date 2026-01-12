package br.com.mv.cccopilotpropertie.embedding.mock;

import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockEmbeddingService implements EmbeddingService {

//    @Override
//    public float[] embed(String text) {
//        float[] vector = new float[10];
//        for (int i = 0; i < vector.length; i++) {
//            vector[i] = (text.length() + i) % 7;
//        }
//        return vector;
//    }

    @Override
    public float[] embed(String text) {
        int dim = EmbeddingConfig.DIMENSION;
        float[] vector = new float[dim];

        for (int i = 0; i < dim; i++) {
            vector[i] = (text.length() + i) % 7;
        }
        return vector;
    }
}