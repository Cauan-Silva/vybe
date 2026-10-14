package com.cauan.vybe.playlist;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistMusicaRepository extends JpaRepository<PlaylistMusica, Long> {

    @EntityGraph(attributePaths = {"musica", "musica.artista", "musica.album"})
    List<PlaylistMusica> findByPlaylistIdOrderByPosicaoAsc(Long playlistId);

    @EntityGraph(attributePaths = {"musica", "musica.album"})
    Optional<PlaylistMusica> findFirstByPlaylistIdOrderByPosicaoAsc(Long playlistId);

    long countByPlaylistId(Long playlistId);

    boolean existsByPlaylistIdAndMusicaId(Long playlistId, Long musicaId);

    Optional<PlaylistMusica> findByIdAndPlaylistId(Long id, Long playlistId);

    @Query("select coalesce(max(i.posicao), 0) from PlaylistMusica i where i.playlist.id = :playlistId")
    int maiorPosicao(@Param("playlistId") Long playlistId);
}
