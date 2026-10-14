package com.cauan.vybe.catalogo;

import java.util.List;

public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    /** Tudo que uma linha da lista de músicas e o player precisam. */
    public record MusicaResponse(Long id,
                                 String titulo,
                                 Long artistaId,
                                 String artistaNome,
                                 Long albumId,
                                 String albumTitulo,
                                 String capaUrl,
                                 int duracaoSegundos,
                                 Origem origem,
                                 String audioUrl,
                                 Long enviadaPorId,
                                 String licencaUrl,
                                 String paginaUrl) {

        public static MusicaResponse de(Musica m) {
            return new MusicaResponse(
                    m.getId(),
                    m.getTitulo(),
                    m.getArtista().getId(),
                    m.getArtista().getNome(),
                    m.getAlbum().getId(),
                    m.getAlbum().getTitulo(),
                    m.getAlbum().capaUrl(),
                    m.getDuracaoSegundos(),
                    m.getOrigem(),
                    m.audioUrl(),
                    m.getEnviadaPor() == null ? null : m.getEnviadaPor().getId(),
                    m.getLicencaUrl(),
                    m.getPaginaUrl());
        }

        public static List<MusicaResponse> de(List<Musica> musicas) {
            return musicas.stream().map(MusicaResponse::de).toList();
        }
    }

    public record ArtistaResumo(Long id, String nome) {
        public static ArtistaResumo de(Artista a) {
            return new ArtistaResumo(a.getId(), a.getNome());
        }
    }

    public record AlbumResumo(Long id, String titulo, Long artistaId, String artistaNome, String capaUrl) {
        public static AlbumResumo de(Album a) {
            return new AlbumResumo(a.getId(), a.getTitulo(), a.getArtista().getId(),
                    a.getArtista().getNome(), a.capaUrl());
        }
    }

    public record ArtistaDetalhe(Long id, String nome, List<AlbumResumo> albuns, List<MusicaResponse> musicas) {
    }

    public record AlbumDetalhe(Long id,
                               String titulo,
                               Long artistaId,
                               String artistaNome,
                               String capaUrl,
                               List<MusicaResponse> musicas) {
    }

    public record BuscaResponse(List<MusicaResponse> musicas,
                                List<ArtistaResumo> artistas,
                                List<AlbumResumo> albuns) {
    }
}
