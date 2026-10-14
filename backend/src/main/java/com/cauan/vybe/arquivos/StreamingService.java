package com.cauan.vybe.arquivos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Entrega o áudio em pedaços, como fazem os serviços de streaming.
 *
 * O navegador pede um trecho com o cabeçalho "Range: bytes=INICIO-FIM".
 * Respondemos 206 (Partial Content) com no máximo um bloco (1 MB por padrão)
 * e o cabeçalho "Content-Range: bytes INICIO-FIM/TOTAL" dizendo qual trecho é.
 * Assim a música começa a tocar sem baixar o arquivo inteiro, e pular para
 * qualquer ponto da barra de progresso só busca o trecho daquele ponto.
 */
@Service
public class StreamingService {

    private final long tamanhoBloco;

    public StreamingService(@Value("${vybe.streaming.bloco-bytes}") long tamanhoBloco) {
        if (tamanhoBloco <= 0 || tamanhoBloco > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("vybe.streaming.bloco-bytes deve ser maior que zero");
        }
        this.tamanhoBloco = tamanhoBloco;
    }

    public ResponseEntity<?> responder(Resource arquivo, MediaType tipo, String cabecalhoRange) {
        long tamanho = tamanhoDe(arquivo);

        if (cabecalhoRange == null || cabecalhoRange.isBlank()) {
            // Sem Range: arquivo inteiro, avisando que aceitamos pedidos por partes.
            return ResponseEntity.ok()
                    .contentType(tipo)
                    .contentLength(tamanho)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(arquivo);
        }

        List<HttpRange> pedidos;
        try {
            pedidos = HttpRange.parseRanges(cabecalhoRange);
        } catch (IllegalArgumentException e) {
            return foraDoArquivo(tamanho);
        }
        if (pedidos.isEmpty()) {
            return responder(arquivo, tipo, null);
        }

        // Atendemos só o primeiro trecho (é o que os players de áudio pedem).
        HttpRange pedido = pedidos.get(0);
        long inicio;
        long fim;
        try {
            inicio = pedido.getRangeStart(tamanho);
            fim = pedido.getRangeEnd(tamanho);
        } catch (IllegalArgumentException e) {
            return foraDoArquivo(tamanho);
        }
        if (inicio >= tamanho || fim < inicio) {
            return foraDoArquivo(tamanho);
        }

        long quantidade = Math.min(fim - inicio + 1, tamanhoBloco);
        long ultimoByte = inicio + quantidade - 1;
        byte[] trecho = lerTrecho(arquivo, inicio, (int) quantidade);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(tipo)
                .contentLength(trecho.length)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + inicio + "-" + ultimoByte + "/" + tamanho)
                .body(trecho);
    }

    /** 416: o trecho pedido está mal escrito ou começa depois do fim do arquivo. */
    private static ResponseEntity<?> foraDoArquivo(long tamanho) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + tamanho)
                .build();
    }

    /** Pula até o início do trecho (sem ler o que vem antes) e lê só a quantidade pedida. */
    private static byte[] lerTrecho(Resource arquivo, long inicio, int quantidade) {
        try (InputStream entrada = arquivo.getInputStream()) {
            entrada.skipNBytes(inicio);
            return entrada.readNBytes(quantidade);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o áudio", e);
        }
    }

    private static long tamanhoDe(Resource arquivo) {
        try {
            return arquivo.contentLength();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}