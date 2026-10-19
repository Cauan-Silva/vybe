package com.cauan.vybe.youtube;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InteresseVideoRepository extends JpaRepository<InteresseVideo, Long> {

    List<InteresseVideo> findByUsuarioIdOrderByCriadoEmAsc(Long usuarioId);

    Optional<InteresseVideo> findByIdAndUsuarioId(Long id, Long usuarioId);
}
