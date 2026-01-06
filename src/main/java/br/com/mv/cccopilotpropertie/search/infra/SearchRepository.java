package br.com.mv.cccopilotpropertie.search.infra;


import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository

public class SearchRepository {


    private final JdbcTemplate jdbc;

    public SearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SearchResult> search(float[] vector, int limit) {
        return jdbc.query("""
                    SELECT path, content, 1 - (embedding <=> ?) AS score
                    FROM code_embeddings
                    ORDER BY embedding <=> ?
                    LIMIT ?
                """, ps -> {
            ps.setObject(1, vector);
            ps.setObject(2, vector);
            ps.setInt(3, limit);
        }, (rs, i) -> new SearchResult(
                rs.getString("path"),
                rs.getString("content"),
                rs.getDouble("score")
        ));
    }


}
