package com.cauan.vybe.youtube;

import com.cauan.vybe.spotify.Correspondencia;
import com.cauan.vybe.youtube.YoutubeDtos.Video;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Escolhe, entre os resultados da busca no YouTube, o vídeo que é a mesma música de uma
 * faixa do Spotify. Pontua título, artista e principalmente a duração (a versão de estúdio
 * tem quase a mesma duração), e desconta versões ao vivo, covers, karaokê, acelerada etc.
 */
public final class Equivalencia {

    private static final Pattern VERSAO_DIFERENTE = Pattern.compile(
            "\\b(live|ao vivo|en vivo|cover|karaoke|karaoke version|instrumental|8d|slowed|sped up|nightcore|reverb|remix|acustico|acoustic)\\b");
    /** Abaixo disso, melhor não tocar nada do que tocar a música errada. */
    static final int PONTOS_MINIMOS = 4;

    private Equivalencia() {
    }

    public static Optional<Video> escolher(List<Video> videos, String titulo, String artista, int duracaoSegundos) {
        return videos.stream()
                .filter(v -> v.id() != null && v.incorporavel() && v.duracaoSegundos() > 0)
                .map(v -> new Pontuado(v, pontos(v, titulo, artista, duracaoSegundos)))
                .filter(p -> p.pontos() >= PONTOS_MINIMOS)
                .max(Comparator.comparingInt(Pontuado::pontos))
                .map(Pontuado::video);
    }

    private record Pontuado(Video video, int pontos) {
    }

    static int pontos(Video v, String titulo, String artista, int duracao) {
        String tituloVideo = Correspondencia.normalizar(v.titulo());
        // normalizar() corta o que vem depois de " - "; para o título do vídeo isso tiraria a música
        String tituloVideoCompleto = Correspondencia.normalizar(v.titulo() == null ? "" : v.titulo().replace(" - ", " "));
        String canal = Correspondencia.normalizar(v.canal());
        String t = Correspondencia.normalizar(titulo);
        String a = Correspondencia.normalizar(artista);

        int pontos = 0;
        if (!t.isEmpty() && (tituloVideoCompleto.contains(t) || tituloVideo.contains(t))) pontos += 3;
        if (!a.isEmpty() && (tituloVideoCompleto.contains(a) || canal.contains(a))) pontos += 2;
        if (duracao > 0) {
            int diferenca = Math.abs(v.duracaoSegundos() - duracao);
            if (diferenca <= 5) pontos += 3;
            else if (diferenca <= 15) pontos += 2;
            else if (diferenca <= 40) pontos += 1;
            else if (diferenca > 90) pontos -= 3;
        }
        // Versões são checadas no título inteiro (a normalização apaga o que está entre parênteses)
        String original = semAcentos(titulo);
        var versao = VERSAO_DIFERENTE.matcher(semAcentos(v.titulo()));
        while (versao.find()) {
            if (!original.contains(versao.group(1))) pontos -= 3; // só se a faixa original não for essa versão
        }
        return pontos;
    }

    private static String semAcentos(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
