package com.cauan.vybe.biblioteca;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CurtidaRepository extends JpaRepository<Curtida, Long> {

    boolean existsByUsuarioIdAndMusicaId(Long usuarioId, Long musicaId);

    void deleteByUsuarioIdAndMusicaId(Long usuarioId, Long musicaId);

    @Query("select c.musica.id from Curtida c where c.usuario.id = :usuarioId")
    List<Long> idsDasMusicas(@Param("usuarioId") Long usuarioId);

    @EntityGraph(attributePaths = {"musica", "musica.artista", "musica.album"})
    List<Curtida> findByUsuarioIdOrderByCriadaEmDesc(Long usuarioId);
}
