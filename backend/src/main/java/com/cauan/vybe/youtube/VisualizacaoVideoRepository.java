package com.cauan.vybe.youtube;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/* No resumo, um vídeo só conta a partir de 30 segundos assistidos (abrir e fechar não vale). */
public interface VisualizacaoVideoRepository extends JpaRepository<VisualizacaoVideo, Long> {

    Optional<VisualizacaoVideo> findByIdAndUsuarioId(Long id, Long usuarioId);

    interface Ponto {
        LocalDateTime getQuando();
        Integer getSegundos();
    }

    interface ContagemVideo {
        String getVideoId();
        String getTitulo();
        String getCanal();
        String getCanalId();
        String getCapaUrl();
        Long getVezes();
        Long getSegundos();
    }

    interface ContagemCanal {
        String getNome();
        String getCanalId();
        Long getVezes();
        Long getSegundos();
    }

    @Query("""
            select v.assistidoEm as quando, v.segundosAssistidos as segundos
            from VisualizacaoVideo v
            where v.usuario.id = :usuarioId and v.assistidoEm >= :inicio and v.assistidoEm < :fim
              and v.segundosAssistidos >= 30
            """)
    List<Ponto> pontos(@Param("usuarioId") Long usuarioId,
                       @Param("inicio") LocalDateTime inicio,
                       @Param("fim") LocalDateTime fim);

    @Query("""
            select v.videoId as videoId, max(v.titulo) as titulo, max(v.canal) as canal,
                   max(v.canalId) as canalId, max(v.capaUrl) as capaUrl,
                   count(v) as vezes, sum(v.segundosAssistidos) as segundos
            from VisualizacaoVideo v
            where v.usuario.id = :usuarioId and v.assistidoEm >= :inicio and v.assistidoEm < :fim
              and v.segundosAssistidos >= 30
            group by v.videoId
            order by count(v) desc, sum(v.segundosAssistidos) desc
            """)
    List<ContagemVideo> topVideos(@Param("usuarioId") Long usuarioId,
                                  @Param("inicio") LocalDateTime inicio,
                                  @Param("fim") LocalDateTime fim,
                                  Pageable pagina);

    @Query("""
            select v.canal as nome, max(v.canalId) as canalId, count(v) as vezes,
                   sum(v.segundosAssistidos) as segundos
            from VisualizacaoVideo v
            where v.usuario.id = :usuarioId and v.assistidoEm >= :inicio and v.assistidoEm < :fim
              and v.segundosAssistidos >= 30 and v.canal is not null
            group by v.canal
            order by sum(v.segundosAssistidos) desc, count(v) desc
            """)
    List<ContagemCanal> topCanais(@Param("usuarioId") Long usuarioId,
                                  @Param("inicio") LocalDateTime inicio,
                                  @Param("fim") LocalDateTime fim,
                                  Pageable pagina);

    @Query("select min(v.assistidoEm) from VisualizacaoVideo v where v.usuario.id = :usuarioId")
    LocalDateTime primeira(@Param("usuarioId") Long usuarioId);
}
