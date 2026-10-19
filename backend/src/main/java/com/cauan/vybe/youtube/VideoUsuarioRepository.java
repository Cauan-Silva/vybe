package com.cauan.vybe.youtube;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoUsuarioRepository extends JpaRepository<VideoUsuario, Long> {

    Optional<VideoUsuario> findByUsuarioIdAndVideoId(Long usuarioId, String videoId);

    List<VideoUsuario> findByUsuarioIdAndAssistirDepoisTrueOrderBySalvoEmDesc(Long usuarioId);

    List<VideoUsuario> findByUsuarioIdAndAssistidoEmIsNotNullOrderByAssistidoEmDesc(Long usuarioId, Pageable pagina);

    /** Limpar o histórico: some do histórico, e quem não está no "Assistir depois" é apagado. */
    @Modifying
    @Query("delete from VideoUsuario v where v.usuario.id = :usuarioId and v.assistirDepois = false")
    void apagarSoDoHistorico(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("update VideoUsuario v set v.assistidoEm = null, v.posicaoSegundos = 0 where v.usuario.id = :usuarioId")
    void zerarHistorico(@Param("usuarioId") Long usuarioId);

    /** Vídeos que a pessoa já abriu (para o "Para você" não repetir o que ela já viu). */
    @Query("select v.videoId from VideoUsuario v where v.usuario.id = :usuarioId and v.assistidoEm is not null")
    List<String> idsAssistidos(@Param("usuarioId") Long usuarioId);
}
