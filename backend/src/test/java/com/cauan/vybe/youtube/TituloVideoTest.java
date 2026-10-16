package com.cauan.vybe.youtube;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TituloVideoTest {

    @Test
    void separaArtistaEMusicaETiraOsEnfeites() {
        var r = TituloVideo.interpretar("Linkin Park - Numb (Official Music Video) [4K UPGRADE]", "Linkin Park");

        assertThat(r.artista()).isEqualTo("Linkin Park");
        assertThat(r.titulo()).isEqualTo("Numb");
    }

    @Test
    void semSeparadorUsaOCanalSemTopicNemVevo() {
        assertThat(TituloVideo.interpretar("Numb", "Linkin Park - Topic").artista()).isEqualTo("Linkin Park");
        assertThat(TituloVideo.interpretar("Hello (Official Video)", "AdeleVEVO"))
                .isEqualTo(new TituloVideo.Interpretacao("Adele", "Hello"));
    }

    @Test
    void decodificaOsCaracteresQueOYouTubeManda() {
        var r = TituloVideo.interpretar("Guns N&#39; Roses &amp; Friends – Patience (Clipe Oficial)", "canal");

        assertThat(r.artista()).isEqualTo("Guns N' Roses & Friends");
        assertThat(r.titulo()).isEqualTo("Patience");
    }

    @Test
    void tituloVazioNaoQuebra() {
        assertThat(TituloVideo.interpretar(null, null)).isEqualTo(new TituloVideo.Interpretacao("YouTube", "Sem título"));
    }
}
