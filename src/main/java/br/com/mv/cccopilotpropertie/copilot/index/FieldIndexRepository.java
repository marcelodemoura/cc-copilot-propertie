package br.com.mv.cccopilotpropertie.copilot.index;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class FieldIndexRepository {

    public void save(FieldLocation location) {

    }

    public List<FieldLocation> findByFieldName(String fieldName) {
        return Collections.emptyList();

    }

}
