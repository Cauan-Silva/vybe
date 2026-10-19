package com.cauan.vybe.arquivos;

import com.cauan.vybe.comum.Excecoes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Arquivos numa pasta do computador (vybe.armazenamento.pasta). É o padrão. */
@Component
@ConditionalOnProperty(name = "vybe.armazenamento.tipo", havingValue = "disco", matchIfMissing = true)
public class DestinoDisco implements Destino {

    private final Path raiz;

    public DestinoDisco(@Value("${vybe.armazenamento.pasta}") String pasta) {
        this.raiz = Path.of(pasta).toAbsolutePath().normalize();
        try {
            for (ArmazenamentoService.Tipo tipo : ArmazenamentoService.Tipo.values()) {
                Files.createDirectories(raiz.resolve(tipo.pasta()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta de armazenamento " + raiz, e);
        }
    }

    @Override
    public void gravar(String chave, InputStream conteudo, long tamanho, String tipoConteudo) {
        Path destino = caminho(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(conteudo, destino);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar o arquivo", e);
        }
    }

    @Override
    public Resource ler(String chave) {
        Path caminho = caminho(chave);
        if (!Files.isReadable(caminho)) {
            throw new Excecoes.NaoEncontrado("Arquivo não encontrado");
        }
        return new FileSystemResource(caminho);
    }

    @Override
    public Optional<URI> enderecoDireto(String chave) {
        return Optional.empty();
    }

    @Override
    public void apagar(String chave) {
        try {
            Files.deleteIfExists(caminho(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível apagar " + chave, e);
        }
    }

    /** Nunca sai da raiz, mesmo com "../" na chave. */
    private Path caminho(String chave) {
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new Excecoes.NaoEncontrado("Arquivo não encontrado");
        }
        return caminho;
    }
}
