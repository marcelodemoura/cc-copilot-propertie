package br.com.mv.cccopilotpropertie;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class TestDbConnection implements CommandLineRunner {

    private final DataSource dataSource;

    public TestDbConnection(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            System.out.println("Conexão com PostgreSQL bem-sucedida! " + conn.getMetaData().getURL());
        } catch (Exception e) {
            System.err.println("Erro ao conectar: " + e.getMessage());
        }
    }
}