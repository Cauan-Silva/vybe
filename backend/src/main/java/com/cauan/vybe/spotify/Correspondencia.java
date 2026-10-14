package com.cauan.vybe.spotify;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Decide se duas músicas são "a mesma" comparando título e artista de forma tolerante:
 * sem acentos, sem maiúsculas, sem pontuação e sem complementos como
 * "(feat. X)", "[Remix]" ou " - Remastered 2011".
 */
public final class Correspondencia {

    private Correspondencia() {
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase(Locale.ROOT)
                .replaceAll("\\(.*?\\)|\\[.*?]", " ")   // (feat. X), [Live]
                .replaceAll("\\s+-\\s+.*$", " ")         // " - Remastered 2011"
                .replaceAll("\\b(feat|ft)\\b.*$", " ")   // feat sem parênteses
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return s;
    }

    /** Mesmo título e o artista bate com algum dos artistas da faixa do Spotify. */
    public static boolean mesmaMusica(String titulo, String artista, String tituloSpotify, List<String> artistasSpotify) {
        String t = normalizar(titulo);
        String a = normalizar(artista);
        if (t.isEmpty() || a.isEmpty() || !t.equals(normalizar(tituloSpotify))) {
            return false;
        }
        return artistasSpotify.stream().map(Correspondencia::normalizar).anyMatch(a::equals);
    }
}
