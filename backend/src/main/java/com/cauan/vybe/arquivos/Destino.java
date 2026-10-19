package com.cauan.vybe.arquivos;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

/**
 * Onde os arquivos enviados (áudios, capas, fotos) ficam de verdade.
 * - DestinoDisco: uma pasta do computador (desenvolvimento e programa para Windows);
 * - DestinoS3: um armazenamento compatível com S3, como o Cloudflare R2 (versão hospedada,
 *   porque o disco dos servidores gratuitos é apagado a cada reinício).
 * As chaves são sempre "pasta/nome-gerado" (ex.: "audio/3f2a...mp3").
 */
public interface Destino {

    void gravar(String chave, InputStream conteudo, long tamanho, String tipoConteudo);

    /** Arquivo para a API entregar ela mesma (disco). */
    Resource ler(String chave);

    /**
     * Endereço para o navegador buscar direto, sem passar pela API (S3/R2). Vazio = a API
     * entrega o arquivo com ler().
     */
    Optional<URI> enderecoDireto(String chave);

    void apagar(String chave);
}
