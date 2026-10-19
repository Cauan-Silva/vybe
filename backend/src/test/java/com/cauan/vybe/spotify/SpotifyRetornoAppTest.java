package com.cauan.vybe.spotify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyRetornoAppTest {

    @Test
    void conectadoAvisaQueDaParaVoltarAoApp() {
        String html = SpotifyRetornoApp.pagina("conectado", null);
        assertThat(html).contains("Spotify conectado").contains("voltar para o app VYBE");
    }

    @Test
    void erroMostraOMotivoSemDeixarInjetarHtml() {
        String html = SpotifyRetornoApp.pagina("erro", "<script>alert(1)</script>");
        assertThat(html).contains("Não foi possível conectar")
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }
}
