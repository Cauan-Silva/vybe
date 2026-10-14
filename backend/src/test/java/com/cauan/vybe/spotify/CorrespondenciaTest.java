package com.cauan.vybe.spotify;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorrespondenciaTest {

    @Test
    void ignoraAcentosMaiusculasEPontuacao() {
        assertThat(Correspondencia.normalizar("Coração, Pé!")).isEqualTo("coracao pe");
    }

    @Test
    void removeComplementosDoTitulo() {
        assertThat(Correspondencia.normalizar("Song (feat. Alguém)")).isEqualTo("song");
        assertThat(Correspondencia.normalizar("Song [Live]")).isEqualTo("song");
        assertThat(Correspondencia.normalizar("Song - Remastered 2011")).isEqualTo("song");
        assertThat(Correspondencia.normalizar("Song feat Alguém")).isEqualTo("song");
    }

    @Test
    void naoCortaPalavrasQueContemFt() {
        assertThat(Correspondencia.normalizar("Daft Punk")).isEqualTo("daft punk");
    }

    @Test
    void mesmaMusicaComparaTituloEQualquerArtista() {
        List<String> artistas = List.of("Artista Principal", "Convidado");

        assertThat(Correspondencia.mesmaMusica("Noite Clara", "convidado", "Noite Clara (Ao Vivo)", artistas)).isTrue();
        assertThat(Correspondencia.mesmaMusica("Noite Clara", "Outra Banda", "Noite Clara", artistas)).isFalse();
        assertThat(Correspondencia.mesmaMusica("Noite Escura", "Convidado", "Noite Clara", artistas)).isFalse();
    }
}
