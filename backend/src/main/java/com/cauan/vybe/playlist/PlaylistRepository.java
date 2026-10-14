package com.cauan.vybe.playlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByDonoIdOrderByAtualizadaEmDesc(Long donoId);

    /** Busca já filtrando pelo dono: playlist de outra pessoa simplesmente "não existe". */
    Optional<Playlist> findByIdAndDonoId(Long id, Long donoId);
}
