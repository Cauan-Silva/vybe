package com.cauan.vybe.arquivos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingServiceTest {

    private static final int TAMANHO = 3_000_000; // ~3 MB
    private static final long BLOCO = 1_048_576;  // 1 MB
    private static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");

    @TempDir
    Path pasta;

    private Resource arquivo;
    private StreamingService service;

    @BeforeEach
    void setUp() throws IOException {
        // Cada byte vale (posição % 251), para conferir que lemos o trecho certo.
        byte[] conteudo = new byte[TAMANHO];
        for (int i = 0; i < TAMANHO; i++) {
            conteudo[i] = (byte) (i % 251);
        }
        Path caminho = pasta.resolve("musica.mp3");
        Files.write(caminho, conteudo);
        arquivo = new FileSystemResource(caminho);
        service = new StreamingService(BLOCO);
    }

    private static byte[] corpo(ResponseEntity<?> resposta) {
        return (byte[]) resposta.getBody();
    }

    @Test
    void semRangeEntregaOArquivoInteiroEAvisaQueAceitaPartes() {
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentLength()).isEqualTo(TAMANHO);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(resposta.getBody()).isSameAs(arquivo);
    }

    @Test
    void rangeAbertoDevolveSoUmBlocoDoComeco() {
        // É o que o elemento <audio> pede ao começar: "do byte 0 até o fim".
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, "bytes=0-");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(corpo(resposta)).hasSize((int) BLOCO);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 0-" + (BLOCO - 1) + "/" + TAMANHO);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MP3);
    }

    @Test
    void pularParaOMeioDaMusicaBuscaSoAqueleTrecho() {
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, "bytes=2000000-");

        byte[] trecho = corpo(resposta);
        assertThat(trecho).hasSize((int) BLOCO);
        assertThat(trecho[0]).isEqualTo((byte) (2_000_000 % 251));
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 2000000-" + (2_000_000 + BLOCO - 1) + "/" + TAMANHO);
    }

    @Test
    void ultimoTrechoMenorQueOBlocoVemCompleto() {
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, "bytes=2900000-");

        assertThat(corpo(resposta)).hasSize(TAMANHO - 2_900_000);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 2900000-" + (TAMANHO - 1) + "/" + TAMANHO);
    }

    @Test
    void trechoPequenoPedidoVemExato() {
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, "bytes=100-199");

        byte[] trecho = corpo(resposta);
        assertThat(trecho).hasSize(100);
        assertThat(trecho[0]).isEqualTo((byte) 100);
        assertThat(trecho[99]).isEqualTo((byte) 199);
    }

    @Test
    void trechoDepoisDoFimRespondeRangeNaoSatisfeito() {
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, "bytes=5000000-");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */" + TAMANHO);
    }

    @Test
    void rangeMalEscritoRespondeRangeNaoSatisfeito() {
        ResponseEntity<?> resposta = service.responder(arquivo, MP3, "bananas");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }
}