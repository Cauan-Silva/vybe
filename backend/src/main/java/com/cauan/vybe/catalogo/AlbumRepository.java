package com.cauan.vybe.catalogo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    Optional<Album> findByArtistaIdAndTituloIgnoreCase(Long artistaId, String titulo);

    @EntityGraph(attributePaths = "artista")
    List<Album> findByArtistaIdOrderByTituloAsc(Long artistaId);

    @EntityGraph(attributePaths = "artista")
    Optional<Album> findWithArtistaById(Long id);

    @EntityGraph(attributePaths = "artista")
    @Query("select a from Album a where lower(a.titulo) like :padrao order by a.titulo")
    List<Album> buscar(@Param("padrao") String padrao, Pageable pagina);
}
