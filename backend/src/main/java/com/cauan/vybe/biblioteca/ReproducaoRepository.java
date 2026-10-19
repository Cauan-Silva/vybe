package com.cauan.vybe.biblioteca;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/*
 * Nas consultas do resumo, uma reprodução só conta a partir de 30 segundos ouvidos (como nos
 * serviços de streaming): pular a música logo no começo não vale. Reproduções antigas, sem o
 * tempo guardado, usam a duração da música.
 */
public interface ReproducaoRepository extends JpaRepository<Reproducao, Long> {

    /** Músicas diferentes, da ouvida mais recentemente para a mais antiga. */
    @Query("""
            select r.musica.id from Reproducao r
            where r.usuario.id = :usuarioId
            group by r.musica.id
            order by max(r.tocadaEm) desc
            """)
    List<Long> idsRecentes(@Param("usuarioId") Long usuarioId, Pageable pagina);

    Optional<Reproducao> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Um ponto por reprodução que conta: quando foi e quanto foi ouvido. */
    interface Ponto {
        LocalDateTime getQuando();
        Integer getSegundos();
    }

    interface Contagem {
        Long getId();
        Long getVezes();
        Long getSegundos();
    }

    interface ContagemArtista {
        Long getId();
        String getNome();
        Long getVezes();
        Long getSegundos();
    }

    interface Distintos {
        Long getMusicas();
        Long getArtistas();
    }

    @Query("""
            select r.tocadaEm as quando, coalesce(r.segundosOuvidos, m.duracaoSegundos) as segundos
            from Reproducao r join r.musica m
            where r.usuario.id = :usuarioId and r.tocadaEm >= :inicio and r.tocadaEm < :fim
              and coalesce(r.segundosOuvidos, m.duracaoSegundos) >= 30
            """)
    List<Ponto> pontos(@Param("usuarioId") Long usuarioId,
                       @Param("inicio") LocalDateTime inicio,
                       @Param("fim") LocalDateTime fim);

    @Query("""
            select m.id as id, count(r) as vezes, sum(coalesce(r.segundosOuvidos, m.duracaoSegundos)) as segundos
            from Reproducao r join r.musica m
            where r.usuario.id = :usuarioId and r.tocadaEm >= :inicio and r.tocadaEm < :fim
              and coalesce(r.segundosOuvidos, m.duracaoSegundos) >= 30
            group by m.id
            order by count(r) desc, sum(coalesce(r.segundosOuvidos, m.duracaoSegundos)) desc
            """)
    List<Contagem> topMusicas(@Param("usuarioId") Long usuarioId,
                              @Param("inicio") LocalDateTime inicio,
                              @Param("fim") LocalDateTime fim,
                              Pageable pagina);

    @Query("""
            select a.id as id, a.nome as nome, count(r) as vezes,
                   sum(coalesce(r.segundosOuvidos, m.duracaoSegundos)) as segundos
            from Reproducao r join r.musica m join m.artista a
            where r.usuario.id = :usuarioId and r.tocadaEm >= :inicio and r.tocadaEm < :fim
              and coalesce(r.segundosOuvidos, m.duracaoSegundos) >= 30
            group by a.id, a.nome
            order by sum(coalesce(r.segundosOuvidos, m.duracaoSegundos)) desc, count(r) desc
            """)
    List<ContagemArtista> topArtistas(@Param("usuarioId") Long usuarioId,
                                      @Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim,
                                      Pageable pagina);

    @Query("""
            select count(distinct m.id) as musicas, count(distinct m.artista.id) as artistas
            from Reproducao r join r.musica m
            where r.usuario.id = :usuarioId and r.tocadaEm >= :inicio and r.tocadaEm < :fim
              and coalesce(r.segundosOuvidos, m.duracaoSegundos) >= 30
            """)
    Distintos distintos(@Param("usuarioId") Long usuarioId,
                        @Param("inicio") LocalDateTime inicio,
                        @Param("fim") LocalDateTime fim);

    @Query("select min(r.tocadaEm) from Reproducao r where r.usuario.id = :usuarioId")
    LocalDateTime primeira(@Param("usuarioId") Long usuarioId);
}
