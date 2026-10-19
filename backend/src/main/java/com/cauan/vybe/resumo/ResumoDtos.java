package com.cauan.vybe.resumo;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;

import java.util.List;

/** "Seu mês no VYBE": o que mais foi ouvido e assistido num mês. */
public final class ResumoDtos {

    private ResumoDtos() {
    }

    public record MusicaTop(MusicaResponse musica, long vezes, long segundos) {
    }

    public record ArtistaTop(Long id, String nome, String capaUrl, long vezes, long segundos) {
    }

    public record VideoTop(String id, String titulo, String canal, String canalId, String capaUrl,
                           long vezes, long segundos) {
    }

    public record CanalTop(String nome, String canalId, long vezes, long segundos) {
    }

    /** Segundos de música e de vídeo num dia do mês (dia = 1 a 31). */
    public record Dia(int dia, long segundosMusica, long segundosVideo) {
    }

    /**
     * mesAnterior/mesSeguinte vêm nulos quando não há para onde ir (antes do primeiro registro
     * ou depois do mês atual). horaFavorita (0 a 23) e diaFavorito (aaaa-mm-dd) são nulos sem dados.
     */
    public record ResumoResponse(String mes,
                                 String mesAnterior,
                                 String mesSeguinte,
                                 long segundosMusica,
                                 long segundosVideo,
                                 long reproducoes,
                                 long musicasDiferentes,
                                 long artistasDiferentes,
                                 long videosAssistidos,
                                 Integer horaFavorita,
                                 String diaFavorito,
                                 List<MusicaTop> musicas,
                                 List<ArtistaTop> artistas,
                                 List<VideoTop> videos,
                                 List<CanalTop> canais,
                                 List<Dia> dias) {
    }

    /** Mais ouvidas do perfil: periodo é "mes" (mês atual) ou "sempre". */
    public record MaisOuvidas(String periodo, List<MusicaTop> musicas, List<ArtistaTop> artistas) {
    }
}
