package com.cauan.vybe.biblioteca;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReproducaoRepository extends JpaRepository<Reproducao, Long> {

    /** Músicas diferentes, da ouvida mais recentemente para a mais antiga. */
    @Query("""
            select r.musica.id from Reproducao r
            where r.usuario.id = :usuarioId
            group by r.musica.id
            order by max(r.tocadaEm) desc
            """)
    List<Long> idsRecentes(@Param("usuarioId") Long usuarioId, Pageable pagina);
}
