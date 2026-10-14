package com.cauan.vybe.spotify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpotifyContaRepository extends JpaRepository<SpotifyConta, Long> {

    Optional<SpotifyConta> findByUsuarioId(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
