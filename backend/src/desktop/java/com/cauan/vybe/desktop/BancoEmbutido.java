package com.cauan.vybe.desktop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Banco do programa: um PostgreSQL de verdade que vem dentro do instalador e roda só
 * enquanto o VYBE está aberto. Os dados ficam na pasta do usuário (vybe.desktop.dados/banco),
 * então sobrevivem a fechar o programa e a atualizações.
 *
 * Como é o mesmo PostgreSQL da versão web, as migrações do Flyway não mudam.
 */
@Configuration
@Profile("desktop")
public class BancoEmbutido {

    private static final Logger log = LoggerFactory.getLogger(BancoEmbutido.class);

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres postgresEmbutido(@Value("${vybe.desktop.dados}") String pastaDados) throws IOException {
        Path banco = Path.of(pastaDados, "banco");
        Path binarios = Path.of(pastaDados, "postgres");
        Files.createDirectories(banco);
        Files.createDirectories(binarios);
        log.info("Iniciando o banco embutido em {}", banco);
        return EmbeddedPostgres.builder()
                .setDataDirectory(banco)
                .setCleanDataDirectory(false) // nunca apagar: são os dados da pessoa
                .setOverrideWorkingDirectory(binarios.toFile()) // binários fora da pasta temporária
                .start();
    }

    /** Spring usa este DataSource no lugar do configurado em application.yml. */
    @Bean(destroyMethod = "close")
    public DataSource dataSource(EmbeddedPostgres postgres) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl("postgres", "postgres"));
        config.setUsername("postgres");
        config.setMaximumPoolSize(5);
        config.setPoolName("vybe-desktop");
        return new HikariDataSource(config);
    }
}
