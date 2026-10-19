package com.cauan.vybe.arquivos;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinoS3Test {

    private static DestinoS3 destino(String urlPublica) {
        return new DestinoS3("https://conta.r2.cloudflarestorage.com", "auto", "vybe", "chave", "segredo",
                urlPublica, Clock.systemUTC());
    }

    @Test
    void comUrlPublicaOEnderecoEFixo() {
        assertThat(destino("https://pub-123.r2.dev/").enderecoDireto("audio/abc.mp3"))
                .contains(URI.create("https://pub-123.r2.dev/audio/abc.mp3"));
    }

    @Test
    void semUrlPublicaGeraEnderecoAssinadoEReaproveita() {
        DestinoS3 s3 = destino("");

        URI primeiro = s3.enderecoDireto("capas/abc.png").orElseThrow();
        URI segundo = s3.enderecoDireto("capas/abc.png").orElseThrow();

        assertThat(primeiro.toString())
                .startsWith("https://conta.r2.cloudflarestorage.com/vybe/capas/abc.png?")
                .contains("X-Amz-Signature=");
        assertThat(segundo).isEqualTo(primeiro); // o navegador consegue usar o cache
    }

    @Test
    void faltandoConfiguracaoNaoSobe() {
        assertThatThrownBy(() -> new DestinoS3("", "auto", "vybe", "chave", "segredo", "", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
