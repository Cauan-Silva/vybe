package com.cauan.vybe.youtube;

import com.cauan.vybe.youtube.YoutubeDtos.Video;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EquivalenciaTest {

    private static Video video(String id, String titulo, String canal, int duracao) {
        return new Video(id, titulo, canal, null, duracao, true);
    }

    @Test
    void preferePelaDuracaoTituloEArtistaEDescontaAoVivo() {
        List<Video> videos = List.of(
                video("aovivo00001", "The Notorious B.I.G. - Big Poppa (Live at Madison 1995)", "Fã Clube", 300),
                video("oficial0001", "The Notorious B.I.G. - Big Poppa (Official Audio)", "The Notorious B.I.G.", 252),
                video("karaoke0001", "Big Poppa Karaoke Version", "Karaoke Hits", 250));

        assertThat(Equivalencia.escolher(videos, "Big Poppa - 2005 Remaster", "The Notorious B.I.G.", 253))
                .map(Video::id).contains("oficial0001");
    }

    @Test
    void naoEscolheNadaSeNenhumBateDeVerdade() {
        List<Video> videos = List.of(video("outra000001", "Outra música qualquer", "Canal", 600));

        assertThat(Equivalencia.escolher(videos, "Beatriz", "2ZDinizz", 180)).isEmpty();
    }

    @Test
    void versaoAoVivoValeQuandoAOriginalTambemE() {
        Video aoVivo = video("aovivo00001", "Bruno & Marrone - Boate Azul (Ao Vivo)", "Bruno & Marrone", 230);

        assertThat(Equivalencia.pontos(aoVivo, "Boate Azul - Ao Vivo", "Bruno & Marrone", 231))
                .isGreaterThanOrEqualTo(Equivalencia.PONTOS_MINIMOS);
    }

    @Test
    void ignoraVideoQueNaoPodeSerEmbutido() {
        Video bloqueado = new Video("bloqueado01", "2ZDinizz - Beatriz", "2ZDinizz", null, 180, false);

        assertThat(Equivalencia.escolher(List.of(bloqueado), "Beatriz", "2ZDinizz", 180)).isEmpty();
    }
}
