package com.cauan.vybe.catalogo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtistaRepository extends JpaRepository<Artista, Long> {

    Optional<Artista> findByNomeIgnoreCase(String nome);

    List<Artista> findAllByOrderByNomeAsc();

    @Query("select a from Artista a where lower(a.nome) like :padrao order by a.nome")
    List<Artista> buscar(@Param("padrao") String padrao, Pageable pagina);
}
