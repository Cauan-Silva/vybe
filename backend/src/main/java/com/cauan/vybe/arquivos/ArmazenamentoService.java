package com.cauan.vybe.arquivos;

import com.cauan.vybe.comum.Excecoes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda áudios e imagens enviados. Aqui ficam as regras (formatos aceitos, tamanho,
 * nomes gerados, apagar só depois do banco confirmar); onde o arquivo mora de verdade
 * fica com o Destino (pasta do computador ou Cloudflare R2).
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

        public String pasta() {
            return pasta;
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

    private final Destino destino;

    public ArmazenamentoService(Destino destino) {
        this.destino = destino;
    }

    /** Valida o formato, salva e devolve o nome gerado. */
    public String salvar(MultipartFile arquivo, Tipo tipo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new Excecoes.RegraNegocio("O arquivo está vazio");
        }
        String extensao = extensaoPermitida(arquivo.getContentType(), tipo);
        if (tipo == Tipo.CAPA && arquivo.getSize() > LIMITE_CAPA) {
            throw new Excecoes.RegraNegocio("A imagem pode ter no máximo 5 MB");
        }

        String nome = UUID.randomUUID() + "." + extensao;
        try (InputStream entrada = arquivo.getInputStream()) {
            destino.gravar(chave(nome, tipo), entrada, arquivo.getSize(), arquivo.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar o arquivo", e);
        }
        return nome;
    }

    public Resource carregar(String nome, Tipo tipo) {
        return destino.ler(chave(nome, tipo));
    }

    /**
     * Na versão hospedada (R2), o endereço para o navegador buscar o arquivo direto; os
     * controllers respondem com um redirecionamento. No disco, vem vazio e a API entrega.
     */
    public Optional<URI> enderecoDireto(String nome, Tipo tipo) {
        return destino.enderecoDireto(chave(nome, tipo));
    }

    public void apagar(String nome, Tipo tipo) {
        if (nome == null) {
            return;
        }
        try {
            destino.apagar(chave(nome, tipo));
        } catch (RuntimeException e) {
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
                    : "Use uma imagem JPG, PNG ou WebP.");
        }
        return extensao;
    }

    /** Os nomes são sempre UUID + extensão; qualquer outra coisa nem chega ao destino. */
    private static String chave(String nome, Tipo tipo) {
        if (nome == null || !nome.matches("[A-Za-z0-9-]{1,80}\\.[a-z0-9]{2,5}")) {
            throw new Excecoes.NaoEncontrado("Arquivo não encontrado");
        }
        return tipo.pasta + "/" + nome;
    }
}
