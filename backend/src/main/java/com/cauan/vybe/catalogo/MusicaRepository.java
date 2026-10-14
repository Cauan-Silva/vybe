package com.cauan.vybe.catalogo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * As consultas carregam artista e álbum junto (EntityGraph) para não fazer
 * uma consulta extra por música ao montar as listas.
 */
public interface MusicaRepository extends JpaRepository<Musica, Long> {

    /** "Chegaram agora" mostra só o que foi enviado; o Jamendo tem a própria seção. */
    @EntityGraph(attributePaths = {"artista", "album"})
    List<Musica> findByOrigemOrderByCriadaEmDesc(Origem origem, Pageable pagina);

    @EntityGraph(attributePaths = {"artista", "album"})
    Optional<Musica> findByOrigemAndIdExterno(Origem origem, String idExterno);

    @EntityGraph(attributePaths = {"artista", "album"})
    Optional<Musica> findWithDetalhesById(Long id);

    @EntityGraph(attributePaths = {"artista", "album"})
    List<Musica> findByAlbumIdOrderByCriadaEmAsc(Long albumId);

    @EntityGraph(attributePaths = {"artista", "album"})
    List<Musica> findByArtistaIdOrderByCriadaEmDesc(Long artistaId);

    @EntityGraph(attributePaths = {"artista", "album"})
    List<Musica> findByIdIn(Collection<Long> ids);

    /**
     * Faixas do Spotify ficam fora da busca: só tocam para quem tem Premium conectado,
     * e aparecem nas playlists importadas e nos mais ouvidos de cada pessoa.
     */
    @EntityGraph(attributePaths = {"artista", "album"})
    @Query("""
            select m from Musica m
            where m.origem <> com.cauan.vybe.catalogo.Origem.SPOTIFY
              and (lower(m.titulo) like :padrao
                   or lower(m.artista.nome) like :padrao
                   or lower(m.album.titulo) like :padrao)
            order by m.titulo
            """)
    List<Musica> buscar(@Param("padrao") String padrao, Pageable pagina);

    long countByAlbumId(Long albumId);

    long countByArtistaId(Long artistaId);
}
