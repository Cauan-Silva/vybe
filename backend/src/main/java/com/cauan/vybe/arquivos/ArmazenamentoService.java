package com.cauan.vybe.arquivos;

import com.cauan.vybe.comum.Excecoes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda áudios e capas numa pasta do servidor (vybe.armazenamento.pasta).
 * Os nomes dos arquivos são gerados aqui (UUID), nunca vêm do usuário,
 * o que evita sobrescrever arquivos ou acessar pastas fora da raiz.
 */
@Service
public class ArmazenamentoService {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoService.class);
    private static final long LIMITE_CAPA = 5L * 1024 * 1024;

    public enum Tipo {
        AUDIO("audio"),
        CAPA("capas");

        private final String pasta;

        Tipo(String pasta) {
            this.pasta = pasta;
        }
    }

    private static final Map<String, String> FORMATOS_AUDIO = Map.of(
            "audio/mpeg", "mp3",
            "audio/mp3", "mp3",
            "audio/wav", "wav",
            "audio/x-wav", "wav",
            "audio/wave", "wav",
            "audio/ogg", "ogg",
            "audio/mp4", "m4a",
            "audio/x-m4a", "m4a",
            "audio/aac", "aac",
            "audio/flac", "flac");

    private static final Map<String, String> FORMATOS_CAPA = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path raiz;

    public ArmazenamentoService(@Value("${vybe.armazenamento.pasta}") String pasta) {
        this.raiz = Path.of(pasta).toAbsolutePath().normalize();
        try {
            for (Tipo tipo : Tipo.values()) {
                Files.createDirectories(raiz.resolve(tipo.pasta));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta de armazenamento " + raiz, e);
        }
    }

    /** Valida o formato, salva e devolve o nome gerado. */
    public String salvar(MultipartFile arquivo, Tipo tipo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new Excecoes.RegraNegocio("O arquivo está vazio");
        }
        String extensao = extensaoPermitida(arquivo.getContentType(), tipo);
        if (tipo == Tipo.CAPA && arquivo.getSize() > LIMITE_CAPA) {
            throw new Excecoes.RegraNegocio("A capa pode ter no máximo 5 MB");
        }

        String nome = UUID.randomUUID() + "." + extensao;
        try (InputStream entrada = arquivo.getInputStream()) {
            Files.copy(entrada, pasta(tipo).resolve(nome));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar o arquivo", e);
        }
        return nome;
    }

    public Resource carregar(String nome, Tipo tipo) {
        Path caminho = pasta(tipo).resolve(nome).normalize();
        if (!caminho.startsWith(pasta(tipo)) || !Files.isReadable(caminho)) {
            throw new Excecoes.NaoEncontrado("Arquivo não encontrado");
        }
        return new FileSystemResource(caminho);
    }

    public void apagar(String nome, Tipo tipo) {
        if (nome == null) {
            return;
        }
        try {
            Path caminho = pasta(tipo).resolve(nome).normalize();
            if (caminho.startsWith(pasta(tipo))) {
                Files.deleteIfExists(caminho);
            }
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}: {}", nome, e.getMessage());
        }
    }

    /**
     * Se a transação do banco falhar depois que o arquivo foi salvo,
     * apaga o arquivo para não deixar lixo na pasta.
     */
    public void apagarSeFalhar(String nome, Tipo tipo) {
        aoTerminarTransacao(sucesso -> {
            if (!sucesso) {
                apagar(nome, tipo);
            }
        });
    }

    /** Apaga o arquivo só depois que a exclusão no banco foi confirmada. */
    public void apagarAposConfirmar(String nome, Tipo tipo) {
        aoTerminarTransacao(sucesso -> {
            if (sucesso) {
                apagar(nome, tipo);
            }
        });
    }

    private void aoTerminarTransacao(java.util.function.Consumer<Boolean> acao) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                acao.accept(status == STATUS_COMMITTED);
            }
        });
    }

    private static String extensaoPermitida(String contentType, Tipo tipo) {
        String tipoNormalizado = contentType == null ? "" : contentType.toLowerCase().split(";")[0].trim();
        Map<String, String> formatos = tipo == Tipo.AUDIO ? FORMATOS_AUDIO : FORMATOS_CAPA;
        String extensao = formatos.get(tipoNormalizado);
        if (extensao == null) {
            throw new Excecoes.RegraNegocio(tipo == Tipo.AUDIO
                    ? "Formato de áudio não suportado. Use MP3, WAV, OGG, M4A, AAC ou FLAC."
                    : "Use uma imagem JPG, PNG ou WebP para a capa.");
        }
        return extensao;
    }

    private Path pasta(Tipo tipo) {
        return raiz.resolve(tipo.pasta);
    }
}
