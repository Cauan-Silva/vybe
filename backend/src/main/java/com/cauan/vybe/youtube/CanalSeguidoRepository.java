package com.cauan.vybe.youtube;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CanalSeguidoRepository extends JpaRepository<CanalSeguido, Long> {

    List<CanalSeguido> findByUsuarioIdOrderBySeguidoEmDesc(Long usuarioId);

    Optional<CanalSeguido> findByUsuarioIdAndCanalId(Long usuarioId, String canalId);

    long countByUsuarioId(Long usuarioId);
}
