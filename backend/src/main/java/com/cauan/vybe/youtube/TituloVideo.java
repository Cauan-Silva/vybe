package com.cauan.vybe.youtube;

import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tira artista e título de um vídeo do YouTube, que não tem esses campos separados.
 *
 * A maioria dos vídeos de música segue "Artista - Música (Official Video)". Quando o título
 * não tem o separador, o artista é o nome do canal (sem " - Topic" e "VEVO").
 */
final class TituloVideo {

    record Interpretacao(String artista, String titulo) {
    }

    private static final List<String> SEPARADORES = List.of(" - ", " – ", " — ");

    /** Enfeites entre parênteses ou colchetes: (Official Video), [Lyrics], (Clipe Oficial)… */
    private static final Pattern ENFEITE = Pattern.compile(
            "[(\\[][^)\\]]*(official|oficial|video|vídeo|clipe|clip|lyric|letra|audio|áudio|visualizer|"
                    + "hd|4k|mv|remaster)[^)\\]]*[)\\]]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private TituloVideo() {
    }

    static Interpretacao interpretar(String tituloBruto, String canalBruto) {
        String titulo = limpar(tituloBruto == null ? "" : HtmlUtils.htmlUnescape(tituloBruto));
        String canal = limparCanal(canalBruto == null ? "" : HtmlUtils.htmlUnescape(canalBruto));

        for (String separador : SEPARADORES) {
            int i = titulo.indexOf(separador);
            if (i > 0) {
                String artista = titulo.substring(0, i).trim();
                String musica = titulo.substring(i + separador.length()).trim();
                if (!artista.isEmpty() && !musica.isEmpty()) {
                    return new Interpretacao(artista, musica);
                }
            }
        }
        return new Interpretacao(canal.isEmpty() ? "YouTube" : canal, titulo.isEmpty() ? "Sem título" : titulo);
    }

    private static String limpar(String texto) {
        return ENFEITE.matcher(texto).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static String limparCanal(String canal) {
        return canal
                .replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("(?i)\\s*vevo$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
